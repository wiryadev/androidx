/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.glance.appwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.RemoteViews
import androidx.annotation.DoNotInline
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.core.os.bundleOf
import androidx.core.widget.RemoteViewsCompat.setTextViewHeight
import androidx.core.widget.RemoteViewsCompat.setTextViewWidth
import androidx.core.widget.RemoteViewsCompat.setViewBackgroundColor
import androidx.core.widget.RemoteViewsCompat.setViewBackgroundColorResource
import androidx.core.widget.RemoteViewsCompat.setViewBackgroundResource
import androidx.core.widget.RemoteViewsCompat.setViewClipToOutline
import androidx.glance.AndroidResourceImageProvider
import androidx.glance.BackgroundModifier
import androidx.glance.GlanceModifier
import androidx.glance.Visibility
import androidx.glance.VisibilityModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.LaunchActivityAction
import androidx.glance.action.LaunchActivityClassAction
import androidx.glance.action.LaunchActivityComponentAction
import androidx.glance.action.toMutableParameters
import androidx.glance.appwidget.action.CompoundButtonAction
import androidx.glance.appwidget.action.LaunchActivityIntentAction
import androidx.glance.appwidget.action.LaunchBroadcastReceiverAction
import androidx.glance.appwidget.action.LaunchBroadcastReceiverActionAction
import androidx.glance.appwidget.action.LaunchBroadcastReceiverClassAction
import androidx.glance.appwidget.action.LaunchBroadcastReceiverComponentAction
import androidx.glance.appwidget.action.LaunchBroadcastReceiverIntentAction
import androidx.glance.appwidget.action.LaunchServiceAction
import androidx.glance.appwidget.action.LaunchServiceClassAction
import androidx.glance.appwidget.action.LaunchServiceComponentAction
import androidx.glance.appwidget.action.LaunchServiceIntentAction
import androidx.glance.appwidget.action.RunCallbackAction
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.unit.DayNightColorProvider
import androidx.glance.layout.HeightModifier
import androidx.glance.layout.PaddingModifier
import androidx.glance.layout.WidthModifier
import androidx.glance.unit.Dimension
import androidx.glance.unit.FixedColorProvider
import androidx.glance.unit.ResourceColorProvider

internal fun applyModifiers(
    translationContext: TranslationContext,
    rv: RemoteViews,
    modifiers: GlanceModifier,
    viewDef: InsertedViewInfo,
) {
    val context = translationContext.context
    var widthModifier: WidthModifier? = null
    var heightModifier: HeightModifier? = null
    var paddingModifiers: PaddingModifier? = null
    var cornerRadius: Dimension? = null
    var visibility = Visibility.Visible
    modifiers.foldIn(Unit) { _, modifier ->
        when (modifier) {
            is ActionModifier ->
                applyAction(translationContext, rv, modifier.action, viewDef.mainViewId)
            is WidthModifier -> widthModifier = modifier
            is HeightModifier -> heightModifier = modifier
            is BackgroundModifier -> applyBackgroundModifier(context, rv, modifier, viewDef)
            is PaddingModifier -> {
                paddingModifiers = paddingModifiers?.let { it + modifier } ?: modifier
            }
            is VisibilityModifier -> visibility = modifier.visibility
            is CornerRadiusModifier -> cornerRadius = modifier.radius
            is AppWidgetBackgroundModifier -> {
                // This modifier is handled somewhere else.
            }
            else -> {
                Log.w(GlanceAppWidgetTag, "Unknown modifier '$modifier', nothing done.")
            }
        }
    }
    applySizeModifiers(translationContext, rv, widthModifier, heightModifier, viewDef)
    cornerRadius?.let { applyRoundedCorners(rv, viewDef.mainViewId, it) }
    paddingModifiers?.let { padding ->
        val absolutePadding = padding.toDp(context.resources).toAbsolute(translationContext.isRtl)
        val displayMetrics = context.resources.displayMetrics
        rv.setViewPadding(
            viewDef.mainViewId,
            absolutePadding.left.toPixels(displayMetrics),
            absolutePadding.top.toPixels(displayMetrics),
            absolutePadding.right.toPixels(displayMetrics),
            absolutePadding.bottom.toPixels(displayMetrics)
        )
    }
    rv.setViewVisibility(viewDef.mainViewId, visibility.toViewVisibility())
}

private fun Visibility.toViewVisibility() =
    when (this) {
        Visibility.Visible -> View.VISIBLE
        Visibility.Invisible -> View.INVISIBLE
        Visibility.Gone -> View.GONE
    }

private fun applyAction(
    translationContext: TranslationContext,
    rv: RemoteViews,
    action: Action,
    @IdRes viewId: Int
) {
    try {
        if (translationContext.isLazyCollectionDescendant) {
            val fillInIntent = getFillInIntentForAction(action, translationContext, viewId)
            if (action is CompoundButtonAction && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ApplyModifiersApi31Impl.setOnCheckedChangeResponse(rv, viewId, fillInIntent)
            } else {
                rv.setOnClickFillInIntent(viewId, fillInIntent)
            }
        } else {
            val pendingIntent = getPendingIntentForAction(action, translationContext)
            if (action is CompoundButtonAction && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ApplyModifiersApi31Impl.setOnCheckedChangeResponse(rv, viewId, pendingIntent)
            } else {
                rv.setOnClickPendingIntent(viewId, pendingIntent)
            }
        }
    } catch (t: Throwable) {
        Log.e(GlanceAppWidgetTag, "Unrecognized Action: $action", t)
    }
}

private fun getPendingIntentForAction(
    action: Action,
    translationContext: TranslationContext,
    editParams: (ActionParameters) -> ActionParameters = { it }
): PendingIntent {
    when (action) {
        is LaunchActivityAction -> {
            return PendingIntent.getActivity(
                translationContext.context,
                0,
                getLaunchActivityIntent(action, translationContext, editParams),
                PendingIntent.FLAG_MUTABLE
            )
        }
        is LaunchServiceAction -> {
            val intent = getLaunchServiceIntent(action, translationContext)
            return if (action.isForegroundService &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ) {
                ApplyModifiersApi26Impl.getForegroundServicePendingIntent(
                    context = translationContext.context,
                    intent = intent
                )
            } else {
                PendingIntent.getService(
                    translationContext.context,
                    0,
                    intent,
                    PendingIntent.FLAG_MUTABLE
                )
            }
        }
        is LaunchBroadcastReceiverAction -> {
            return PendingIntent.getBroadcast(
                translationContext.context,
                0,
                getLaunchBroadcastReceiverIntent(action, translationContext),
                PendingIntent.FLAG_MUTABLE
            )
        }
        is RunCallbackAction -> {
            return ActionCallbackBroadcastReceiver.createPendingIntent(
                translationContext.context,
                action.callbackClass,
                translationContext.appWidgetId,
                editParams(action.parameters)
            )
        }
        is CompoundButtonAction -> {
            return getPendingIntentForAction(
                action.innerAction,
                translationContext,
                action.getActionParameters()
            )
        }
        else -> error("Cannot create PendingIntent for action type: $action")
    }
}

private fun getFillInIntentForAction(
    action: Action,
    translationContext: TranslationContext,
    @IdRes viewId: Int,
    editParams: (ActionParameters) -> ActionParameters = { it }
): Intent = when (action) {
    is LaunchActivityAction -> {
        val launchActivityIntent = getLaunchActivityIntent(
            action = action,
            translationContext = translationContext,
            editParams = editParams
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Fill in the pending intent template with the action intent directly, with a
            // unique identifier to ensure unique filterEquals
            ApplyModifiersApi29Impl.setIntentIdentifier(launchActivityIntent, viewId)
        } else {
            launchActivityIntent.applyTrampolineIntent(
                context = translationContext.context,
                viewId = viewId,
                type = ListAdapterTrampolineType.ACTIVITY
            )
        }
    }
    is LaunchServiceAction -> getLaunchServiceIntent(
        action = action,
        translationContext = translationContext
    ).applyTrampolineIntent(
        context = translationContext.context,
        viewId = viewId,
        type = if (action.isForegroundService) {
            ListAdapterTrampolineType.FOREGROUND_SERVICE
        } else {
            ListAdapterTrampolineType.SERVICE
        }
    )
    is LaunchBroadcastReceiverAction -> getLaunchBroadcastReceiverIntent(
        action = action,
        translationContext = translationContext
    ).applyTrampolineIntent(
        context = translationContext.context,
        viewId = viewId,
        type = ListAdapterTrampolineType.BROADCAST
    )
    is RunCallbackAction -> ActionCallbackBroadcastReceiver.createIntent(
        context = translationContext.context,
        callbackClass = action.callbackClass,
        appWidgetId = translationContext.appWidgetId,
        parameters = editParams(action.parameters)
    ).applyTrampolineIntent(
        context = translationContext.context,
        viewId = viewId,
        type = ListAdapterTrampolineType.BROADCAST
    )
    is CompoundButtonAction -> getFillInIntentForAction(
        action.innerAction,
        translationContext,
        viewId,
        action.getActionParameters()
    )
    else -> error("Cannot create fill-in Intent for action type: $action")
}

private fun CompoundButtonAction.getActionParameters() = { params: ActionParameters ->
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        params.toMutableParameters().apply {
            set(ToggleableStateKey, !checked)
        }
    } else {
        params
    }
}

private fun getLaunchBroadcastReceiverIntent(
    action: LaunchBroadcastReceiverAction,
    translationContext: TranslationContext
) = when (action) {
    is LaunchBroadcastReceiverComponentAction -> Intent().setComponent(action.componentName)
    is LaunchBroadcastReceiverClassAction ->
        Intent(translationContext.context, action.receiverClass)
    is LaunchBroadcastReceiverIntentAction -> action.intent
    is LaunchBroadcastReceiverActionAction ->
        Intent(action.action).setComponent(action.componentName)
}

private fun getLaunchServiceIntent(
    action: LaunchServiceAction,
    translationContext: TranslationContext
): Intent = when (action) {
    is LaunchServiceComponentAction -> Intent().setComponent(action.componentName)
    is LaunchServiceClassAction ->
        Intent(translationContext.context, action.serviceClass)
    is LaunchServiceIntentAction -> action.intent
}

private fun getLaunchActivityIntent(
    action: LaunchActivityAction,
    translationContext: TranslationContext,
    editParams: (ActionParameters) -> ActionParameters = { it }
): Intent {
    val activityIntent = when (action) {
        is LaunchActivityComponentAction -> Intent().setComponent(action.componentName)
        is LaunchActivityClassAction ->
            Intent(translationContext.context, action.activityClass)
        is LaunchActivityIntentAction -> action.intent
        else -> error("Action type not defined in app widget package: $action")
    }

    val parametersPairs = editParams(action.parameters).asMap().map { (key, value) ->
        key.name to value
    }.toTypedArray()

    activityIntent.putExtras(bundleOf(*parametersPairs))
    return activityIntent
}

private fun applySizeModifiers(
    translationContext: TranslationContext,
    rv: RemoteViews,
    widthModifier: WidthModifier?,
    heightModifier: HeightModifier?,
    viewDef: InsertedViewInfo
) {
    val context = translationContext.context
    if (viewDef.isSimple) {
        widthModifier?.let { applySimpleWidthModifier(context, rv, it, viewDef.mainViewId) }
        heightModifier?.let { applySimpleHeightModifier(context, rv, it, viewDef.mainViewId) }
        return
    }

    check(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        "There is currently no valid use case where a complex view is used on Android S"
    }

    val width = widthModifier?.width
    val height = heightModifier?.height

    if (!(width.isFixed || height.isFixed)) {
        // The sizing view is only present and needed for setting fixed dimensions.
        return
    }

    val useMatchSizeWidth = width is Dimension.Fill || width is Dimension.Expand
    val useMatchSizeHeight = height is Dimension.Fill || height is Dimension.Expand
    val sizeViewLayout = when {
        useMatchSizeWidth && useMatchSizeHeight -> R.layout.size_match_match
        useMatchSizeWidth -> R.layout.size_match_wrap
        useMatchSizeHeight -> R.layout.size_wrap_match
        else -> R.layout.size_wrap_wrap
    }

    val sizeTargetViewId = rv.inflateViewStub(translationContext, R.id.sizeViewStub, sizeViewLayout)

    fun Dimension.Dp.toPixels() = dp.toPixels(context)
    fun Dimension.Resource.toPixels() = context.resources.getDimensionPixelSize(res)
    when (width) {
        is Dimension.Dp -> rv.setTextViewWidth(sizeTargetViewId, width.toPixels())
        is Dimension.Resource -> rv.setTextViewWidth(sizeTargetViewId, width.toPixels())
        Dimension.Expand, Dimension.Fill, Dimension.Wrap, null -> {
        }
    }.let {}
    when (height) {
        is Dimension.Dp -> rv.setTextViewHeight(sizeTargetViewId, height.toPixels())
        is Dimension.Resource -> rv.setTextViewHeight(sizeTargetViewId, height.toPixels())
        Dimension.Expand, Dimension.Fill, Dimension.Wrap, null -> {
        }
    }.let {}
}

internal fun applySimpleWidthModifier(
    context: Context,
    rv: RemoteViews,
    modifier: WidthModifier,
    viewId: Int,
) {
    val width = modifier.width
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // Prior to Android S, these layouts already have the appropriate attribute in the xml, so
        // no action is needed.
        if (
            width.resolveDimension(context) in listOf(
                Dimension.Wrap,
                Dimension.Fill,
                Dimension.Expand
            )
        ) {
            return
        }
        throw IllegalArgumentException(
            "Using a width of $width requires a complex layout before API 31"
        )
    }
    // Wrap and Expand are done in XML on Android S+
    if (width in listOf(Dimension.Wrap, Dimension.Expand)) return
    ApplyModifiersApi31Impl.setViewWidth(rv, viewId, width)
}

internal fun applySimpleHeightModifier(
    context: Context,
    rv: RemoteViews,
    modifier: HeightModifier,
    viewId: Int,
) {
    // These layouts already have the appropriate attribute in the xml, so no action is needed.
    val height = modifier.height
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // Prior to Android S, these layouts already have the appropriate attribute in the xml, so
        // no action is needed.
        if (
            height.resolveDimension(context) in listOf(
                Dimension.Wrap,
                Dimension.Fill,
                Dimension.Expand
            )
        ) {
            return
        }
        throw IllegalArgumentException(
            "Using a height of $height requires a complex layout before API 31"
        )
    }
    // Wrap and Expand are done in XML on Android S+
    if (height in listOf(Dimension.Wrap, Dimension.Expand)) return
    ApplyModifiersApi31Impl.setViewHeight(rv, viewId, height)
}

private fun applyBackgroundModifier(
    context: Context,
    rv: RemoteViews,
    modifier: BackgroundModifier,
    viewDef: InsertedViewInfo
) {
    val viewId = viewDef.mainViewId
    val imageProvider = modifier.imageProvider
    if (imageProvider != null) {
        if (imageProvider is AndroidResourceImageProvider) {
            rv.setViewBackgroundResource(viewId, imageProvider.resId)
        }
        // Otherwise, the background has been transformed and should be ignored
        // (removing modifiers is not really possible).
        return
    }
    when (val colorProvider = modifier.colorProvider) {
        is FixedColorProvider -> rv.setViewBackgroundColor(viewId, colorProvider.color.toArgb())
        is ResourceColorProvider -> rv.setViewBackgroundColorResource(
            viewId,
            colorProvider.resId
        )
        is DayNightColorProvider -> {
            if (Build.VERSION.SDK_INT >= 31) {
                rv.setViewBackgroundColor(
                    viewId,
                    colorProvider.day.toArgb(),
                    colorProvider.night.toArgb()
                )
            } else {
                rv.setViewBackgroundColor(viewId, colorProvider.resolve(context).toArgb())
            }
        }
        else -> Log.w(GlanceAppWidgetTag, "Unexpected background color modifier: $colorProvider")
    }
}

private val Dimension?.isFixed: Boolean
    get() = when (this) {
        is Dimension.Dp, is Dimension.Resource -> true
        Dimension.Expand, Dimension.Fill, Dimension.Wrap, null -> false
    }

private fun applyRoundedCorners(rv: RemoteViews, viewId: Int, radius: Dimension) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ApplyModifiersApi31Impl.applyRoundedCorners(rv, viewId, radius)
        return
    }
    Log.w(GlanceAppWidgetTag, "Cannot set the rounded corner of views before Api 31.")
}

@RequiresApi(Build.VERSION_CODES.S)
private object ApplyModifiersApi31Impl {
    @DoNotInline
    fun setViewWidth(rv: RemoteViews, viewId: Int, width: Dimension) {
        when (width) {
            is Dimension.Wrap -> {
                rv.setViewLayoutWidth(viewId, WRAP_CONTENT.toFloat(), COMPLEX_UNIT_PX)
            }
            is Dimension.Expand -> rv.setViewLayoutWidth(viewId, 0f, COMPLEX_UNIT_PX)
            is Dimension.Dp -> rv.setViewLayoutWidth(viewId, width.dp.value, COMPLEX_UNIT_DIP)
            is Dimension.Resource -> rv.setViewLayoutWidthDimen(viewId, width.res)
            Dimension.Fill -> {
                rv.setViewLayoutWidth(viewId, MATCH_PARENT.toFloat(), COMPLEX_UNIT_PX)
            }
        }.let {}
    }

    @DoNotInline
    fun setViewHeight(rv: RemoteViews, viewId: Int, height: Dimension) {
        when (height) {
            is Dimension.Wrap -> {
                rv.setViewLayoutHeight(viewId, WRAP_CONTENT.toFloat(), COMPLEX_UNIT_PX)
            }
            is Dimension.Expand -> rv.setViewLayoutHeight(viewId, 0f, COMPLEX_UNIT_PX)
            is Dimension.Dp -> rv.setViewLayoutHeight(viewId, height.dp.value, COMPLEX_UNIT_DIP)
            is Dimension.Resource -> rv.setViewLayoutHeightDimen(viewId, height.res)
            Dimension.Fill -> {
                rv.setViewLayoutHeight(viewId, MATCH_PARENT.toFloat(), COMPLEX_UNIT_PX)
            }
        }.let {}
    }

    @DoNotInline
    fun applyRoundedCorners(rv: RemoteViews, viewId: Int, radius: Dimension) {
        rv.setViewClipToOutline(viewId, true)
        when (radius) {
            is Dimension.Dp -> {
                rv.setViewOutlinePreferredRadius(viewId, radius.dp.value, COMPLEX_UNIT_DIP)
            }
            is Dimension.Resource -> {
                rv.setViewOutlinePreferredRadiusDimen(viewId, radius.res)
            }
            else -> error("Rounded corners should not be ${radius.javaClass.canonicalName}")
        }
    }

    @DoNotInline
    fun setOnCheckedChangeResponse(rv: RemoteViews, viewId: Int, intent: PendingIntent) {
        rv.setOnCheckedChangeResponse(viewId, RemoteViews.RemoteResponse.fromPendingIntent(intent))
    }

    @DoNotInline
    fun setOnCheckedChangeResponse(rv: RemoteViews, viewId: Int, intent: Intent) {
        rv.setOnCheckedChangeResponse(viewId, RemoteViews.RemoteResponse.fromFillInIntent(intent))
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private object ApplyModifiersApi29Impl {
    @DoNotInline
    fun setIntentIdentifier(intent: Intent, viewId: Int): Intent = intent.apply {
        identifier = viewId.toString()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private object ApplyModifiersApi26Impl {
    @DoNotInline
    fun getForegroundServicePendingIntent(context: Context, intent: Intent): PendingIntent {
        return PendingIntent.getForegroundService(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )
    }
}