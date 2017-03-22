/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.support.v17.leanback.app;

import static junit.framework.TestCase.assertFalse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.animation.PropertyValuesHolder;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.v17.leanback.R;
import android.support.v17.leanback.graphics.FitWidthBitmapDrawable;
import android.support.v17.leanback.media.MediaPlayerGlue;
import android.support.v17.leanback.testutils.PollingCheck;
import android.support.v17.leanback.widget.DetailsParallax;
import android.support.v17.leanback.widget.DetailsParallaxDrawable;
import android.support.v17.leanback.widget.ParallaxTarget;
import android.support.v17.leanback.widget.RecyclerViewParallax;
import android.support.v17.leanback.widget.VerticalGridView;
import android.view.KeyEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link DetailsFragment}.
 */
@RunWith(JUnit4.class)
@LargeTest
public class DetailsFragmentTest extends SingleFragmentTestBase {

    static final int PARALLAX_VERTICAL_OFFSET = -300;

    static int getCoverDrawableAlpha(DetailsFragmentBackgroundController controller) {
        return ((FitWidthBitmapDrawable) controller.mParallaxDrawable.getCoverDrawable())
                .getAlpha();
    }

    public static class DetailsFragmentParallax extends DetailsTestFragment {

        private DetailsParallaxDrawable mParallaxDrawable;

        public DetailsFragmentParallax() {
            super();
            mMinVerticalOffset = PARALLAX_VERTICAL_OFFSET;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Drawable coverDrawable = new FitWidthBitmapDrawable();
            mParallaxDrawable = new DetailsParallaxDrawable(
                    getActivity(),
                    getParallax(),
                    coverDrawable,
                    new ParallaxTarget.PropertyValuesHolderTarget(
                            coverDrawable,
                            PropertyValuesHolder.ofInt("verticalOffset", 0, mMinVerticalOffset)
                    )
            );

            BackgroundManager backgroundManager = BackgroundManager.getInstance(getActivity());
            backgroundManager.attach(getActivity().getWindow());
            backgroundManager.setDrawable(mParallaxDrawable);
        }

        @Override
        public void onStart() {
            super.onStart();
            setItem(new PhotoItem("Hello world", "Fake content goes here",
                    android.support.v17.leanback.test.R.drawable.spiderman));
        }

        @Override
        public void onResume() {
            super.onResume();
            Bitmap bitmap = BitmapFactory.decodeResource(getActivity().getResources(),
                    android.support.v17.leanback.test.R.drawable.spiderman);
            ((FitWidthBitmapDrawable) mParallaxDrawable.getCoverDrawable()).setBitmap(bitmap);
        }

        DetailsParallaxDrawable getParallaxDrawable() {
            return mParallaxDrawable;
        }
    }

    @Test
    public void parallaxSetupTest() {
        launchAndWaitActivity(DetailsFragmentTest.DetailsFragmentParallax.class,
                new SingleFragmentTestBase.Options().uiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);

        double delta = 0.0002;
        DetailsParallax dpm = ((DetailsFragment) mActivity.getTestFragment()).getParallax();

        RecyclerViewParallax.ChildPositionProperty frameTop =
                (RecyclerViewParallax.ChildPositionProperty) dpm.getOverviewRowTop();
        assertEquals(0f, frameTop.getFraction(), delta);
        assertEquals(0f, frameTop.getAdapterPosition(), delta);


        RecyclerViewParallax.ChildPositionProperty frameBottom =
                (RecyclerViewParallax.ChildPositionProperty) dpm.getOverviewRowBottom();
        assertEquals(1f, frameBottom.getFraction(), delta);
        assertEquals(0f, frameBottom.getAdapterPosition(), delta);
    }

    @Test
    public void parallaxTest() throws Throwable {
        launchAndWaitActivity(DetailsFragmentParallax.class,
                new Options().uiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);

        final DetailsFragmentParallax detailsFragment =
                (DetailsFragmentParallax) mActivity.getTestFragment();
        DetailsParallaxDrawable drawable =
                detailsFragment.getParallaxDrawable();
        final FitWidthBitmapDrawable bitmapDrawable = (FitWidthBitmapDrawable)
                drawable.getCoverDrawable();

        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return detailsFragment.getRowsFragment().getAdapter() != null
                        && detailsFragment.getRowsFragment().getAdapter().size() > 1;
            }
        });

        final VerticalGridView verticalGridView = detailsFragment.getRowsFragment()
                .getVerticalGridView();
        final int windowHeight = verticalGridView.getHeight();
        final int windowWidth = verticalGridView.getWidth();
        // make sure background manager attached to window is same size as VerticalGridView
        // i.e. no status bar.
        assertEquals(windowHeight, mActivity.getWindow().getDecorView().getHeight());
        assertEquals(windowWidth, mActivity.getWindow().getDecorView().getWidth());

        final View detailsFrame = verticalGridView.findViewById(R.id.details_frame);

        assertEquals(windowWidth, bitmapDrawable.getBounds().width());

        final Rect detailsFrameRect = new Rect();
        detailsFrameRect.set(0, 0, detailsFrame.getWidth(), detailsFrame.getHeight());
        verticalGridView.offsetDescendantRectToMyCoords(detailsFrame, detailsFrameRect);

        assertEquals(Math.min(windowHeight, detailsFrameRect.top),
                bitmapDrawable.getBounds().height());
        assertEquals(0, bitmapDrawable.getVerticalOffset());

        assertTrue("TitleView is visible", detailsFragment.getView()
                .findViewById(R.id.browse_title_group).getVisibility() == View.VISIBLE);

        activityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                verticalGridView.scrollToPosition(1);
            }
        });

        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return bitmapDrawable.getVerticalOffset() == PARALLAX_VERTICAL_OFFSET
                        && detailsFragment.getView()
                        .findViewById(R.id.browse_title_group).getVisibility() != View.VISIBLE;
            }
        });

        detailsFrameRect.set(0, 0, detailsFrame.getWidth(), detailsFrame.getHeight());
        verticalGridView.offsetDescendantRectToMyCoords(detailsFrame, detailsFrameRect);

        assertEquals(0, bitmapDrawable.getBounds().top);
        assertEquals(Math.max(detailsFrameRect.top, 0), bitmapDrawable.getBounds().bottom);
        assertEquals(windowWidth, bitmapDrawable.getBounds().width());

        ColorDrawable colorDrawable = (ColorDrawable) (drawable.getChildAt(1).getDrawable());
        assertEquals(windowWidth, colorDrawable.getBounds().width());
        assertEquals(detailsFrameRect.bottom, colorDrawable.getBounds().top);
        assertEquals(windowHeight, colorDrawable.getBounds().bottom);
    }

    public static class DetailsFragmentWithVideo extends DetailsTestFragment {

        final DetailsFragmentBackgroundController mDetailsBackground =
                new DetailsFragmentBackgroundController(this);
        MediaPlayerGlue mGlue;

        public DetailsFragmentWithVideo() {
            mTimeToLoadOverviewRow = mTimeToLoadRelatedRow = 100;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDetailsBackground.enableParallax();
            mGlue = new MediaPlayerGlue(getActivity());
            mDetailsBackground.setupVideoPlayback(mGlue);

            mGlue.setMode(MediaPlayerGlue.REPEAT_ALL);
            mGlue.setArtist("A Googleer");
            mGlue.setTitle("Diving with Sharks");
            mGlue.setMediaSource(
                    Uri.parse("android.resource://android.support.v17.leanback.test/raw/video"));
        }

        @Override
        public void onStart() {
            super.onStart();
            Bitmap bitmap = BitmapFactory.decodeResource(getActivity().getResources(),
                    android.support.v17.leanback.test.R.drawable.spiderman);
            mDetailsBackground.setCoverBitmap(bitmap);
        }

        @Override
        public void onStop() {
            mDetailsBackground.setCoverBitmap(null);
            super.onStop();
        }
    }

    public static class DetailsFragmentWithVideo1 extends DetailsFragmentWithVideo {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setItem(new PhotoItem("Hello world", "Fake content goes here",
                    android.support.v17.leanback.test.R.drawable.spiderman));
        }
    }

    public static class DetailsFragmentWithVideo2 extends DetailsFragmentWithVideo {

        @Override
        public void onStart() {
            super.onStart();
            setItem(new PhotoItem("Hello world", "Fake content goes here",
                    android.support.v17.leanback.test.R.drawable.spiderman));
        }
    }

    private void navigateBetweenRowsAndVideoUsingRequestFocusInternal(Class cls)
            throws Throwable {
        launchAndWaitActivity(cls,
                new Options().uiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);

        final DetailsFragmentWithVideo detailsFragment =
                (DetailsFragmentWithVideo) mActivity.getTestFragment();
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return detailsFragment.mVideoFragment != null
                        && detailsFragment.mVideoFragment.getView() != null
                        && detailsFragment.mGlue.isMediaPlaying();
            }
        });

        final int screenHeight = detailsFragment.getRowsFragment().getVerticalGridView()
                .getHeight();
        final View firstRow = detailsFragment.getRowsFragment().getVerticalGridView().getChildAt(0);
        final int originalFirstRowTop = firstRow.getTop();
        assertTrue(firstRow.hasFocus());
        assertTrue(firstRow.getTop() > 0 && firstRow.getTop() < screenHeight);
        assertTrue(detailsFragment.isShowingTitle());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                detailsFragment.mVideoFragment.getView().requestFocus();
            }
        });
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return firstRow.getTop() >= screenHeight;
            }
        });
        assertFalse(detailsFragment.isShowingTitle());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                detailsFragment.getRowsFragment().getVerticalGridView().requestFocus();
            }
        });
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return firstRow.getTop() == originalFirstRowTop;
            }
        });
        assertTrue(detailsFragment.isShowingTitle());
    }

    @Test
    public void navigateBetweenRowsAndVideoUsingRequestFocus1() throws Throwable {
        navigateBetweenRowsAndVideoUsingRequestFocusInternal(DetailsFragmentWithVideo1.class);
    }

    @Test
    public void navigateBetweenRowsAndVideoUsingRequestFocus2() throws Throwable {
        navigateBetweenRowsAndVideoUsingRequestFocusInternal(DetailsFragmentWithVideo2.class);
    }

    private void navigateBetweenRowsAndVideoUsingDPADInternal(Class cls) throws Throwable {
        launchAndWaitActivity(cls,
                new Options().uiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);

        final DetailsFragmentWithVideo detailsFragment =
                (DetailsFragmentWithVideo) mActivity.getTestFragment();
        // wait video playing
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return detailsFragment.mVideoFragment != null
                        && detailsFragment.mVideoFragment.getView() != null
                        && detailsFragment.mGlue.isMediaPlaying();
            }
        });

        final int screenHeight = detailsFragment.getRowsFragment().getVerticalGridView()
                .getHeight();
        final View firstRow = detailsFragment.getRowsFragment().getVerticalGridView().getChildAt(0);
        final int originalFirstRowTop = firstRow.getTop();
        assertTrue(firstRow.hasFocus());
        assertTrue(firstRow.getTop() > 0 && firstRow.getTop() < screenHeight);
        assertTrue(detailsFragment.isShowingTitle());

        // navigate to video
        sendKeys(KeyEvent.KEYCODE_DPAD_UP);
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return firstRow.getTop() >= screenHeight;
            }
        });

        // wait auto hide play controls done:
        PollingCheck.waitFor(8000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return ((PlaybackFragment) detailsFragment.mVideoFragment).mBgAlpha == 0;
            }
        });

        // navigate to details
        sendKeys(KeyEvent.KEYCODE_BACK);
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return firstRow.getTop() == originalFirstRowTop;
            }
        });
        assertTrue(detailsFragment.isShowingTitle());
    }

    @Test
    public void navigateBetweenRowsAndVideoUsingDPAD1() throws Throwable {
        navigateBetweenRowsAndVideoUsingDPADInternal(DetailsFragmentWithVideo1.class);
    }

    @Test
    public void navigateBetweenRowsAndVideoUsingDPAD2() throws Throwable {
        navigateBetweenRowsAndVideoUsingDPADInternal(DetailsFragmentWithVideo2.class);
    }

    public static class EmptyFragmentClass extends Fragment {
        @Override
        public void onStart() {
            super.onStart();
            getActivity().finish();
        }
    }

    private void fragmentOnStartWithVideoInternal(Class cls) throws Throwable {
        launchAndWaitActivity(cls,
                new Options().uiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);

        final DetailsFragmentWithVideo detailsFragment =
                (DetailsFragmentWithVideo) mActivity.getTestFragment();
        // wait video playing
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return detailsFragment.mVideoFragment != null
                        && detailsFragment.mVideoFragment.getView() != null
                        && detailsFragment.mGlue.isMediaPlaying();
            }
        });

        final int screenHeight = detailsFragment.getRowsFragment().getVerticalGridView()
                .getHeight();
        final View firstRow = detailsFragment.getRowsFragment().getVerticalGridView().getChildAt(0);
        final int originalFirstRowTop = firstRow.getTop();
        assertTrue(firstRow.hasFocus());
        assertTrue(firstRow.getTop() > 0 && firstRow.getTop() < screenHeight);
        assertTrue(detailsFragment.isShowingTitle());

        // navigate to video
        sendKeys(KeyEvent.KEYCODE_DPAD_UP);
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return firstRow.getTop() >= screenHeight;
            }
        });

        // start an empty activity
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(mActivity, SingleFragmentTestActivity.class);
                        intent.putExtra(SingleFragmentTestActivity.EXTRA_FRAGMENT_NAME,
                                EmptyFragmentClass.class.getName());
                        mActivity.startActivity(intent);
                    }
                }
        );
        PollingCheck.waitFor(2000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return detailsFragment.isResumed();
            }
        });
        assertTrue(detailsFragment.mVideoFragment.getView().hasFocus());
    }

    @Test
    public void fragmentOnStartWithVideo1() throws Throwable {
        fragmentOnStartWithVideoInternal(DetailsFragmentWithVideo1.class);
    }

    @Test
    public void fragmentOnStartWithVideo2() throws Throwable {
        fragmentOnStartWithVideoInternal(DetailsFragmentWithVideo2.class);
    }

    @Test
    public void navigateBetweenRowsAndTitle() throws Throwable {
        launchAndWaitActivity(DetailsTestFragment.class, new Options().uiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);
        final DetailsTestFragment detailsFragment =
                (DetailsTestFragment) mActivity.getTestFragment();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                detailsFragment.setOnSearchClickedListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                    }
                });
                detailsFragment.setItem(new PhotoItem("Hello world", "Fake content goes here",
                        android.support.v17.leanback.test.R.drawable.spiderman));
            }
        });

        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return detailsFragment.getRowsFragment().getVerticalGridView().getChildCount() > 0;
            }
        });
        final View firstRow = detailsFragment.getRowsFragment().getVerticalGridView().getChildAt(0);
        final int originalFirstRowTop = firstRow.getTop();
        final int screenHeight = detailsFragment.getRowsFragment().getVerticalGridView()
                .getHeight();

        assertTrue(firstRow.hasFocus());
        assertTrue(detailsFragment.isShowingTitle());
        assertTrue(firstRow.getTop() > 0 && firstRow.getTop() < screenHeight);

        sendKeys(KeyEvent.KEYCODE_DPAD_UP);
        PollingCheck.waitFor(new PollingCheck.ViewStableOnScreen(firstRow));
        assertTrue(detailsFragment.isShowingTitle());
        assertTrue(detailsFragment.getTitleView().hasFocus());
        assertEquals(originalFirstRowTop, firstRow.getTop());

        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        PollingCheck.waitFor(new PollingCheck.ViewStableOnScreen(firstRow));
        assertTrue(detailsFragment.isShowingTitle());
        assertTrue(firstRow.hasFocus());
        assertEquals(originalFirstRowTop, firstRow.getTop());
    }

    public static class DetailsFragmentWithNoVideo extends DetailsTestFragment {

        final DetailsFragmentBackgroundController mDetailsBackground =
                new DetailsFragmentBackgroundController(this);

        public DetailsFragmentWithNoVideo() {
            mTimeToLoadOverviewRow = mTimeToLoadRelatedRow = 100;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDetailsBackground.enableParallax();

            setItem(new PhotoItem("Hello world", "Fake content goes here",
                    android.support.v17.leanback.test.R.drawable.spiderman));
        }

        @Override
        public void onStart() {
            super.onStart();
            Bitmap bitmap = BitmapFactory.decodeResource(getActivity().getResources(),
                    android.support.v17.leanback.test.R.drawable.spiderman);
            mDetailsBackground.setCoverBitmap(bitmap);
        }

        @Override
        public void onStop() {
            mDetailsBackground.setCoverBitmap(null);
            super.onStop();
        }
    }

    @Test
    public void lateSetupVideo() {
        launchAndWaitActivity(DetailsFragmentWithNoVideo.class, new Options().uiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);
        final DetailsFragmentWithNoVideo detailsFragment =
                (DetailsFragmentWithNoVideo) mActivity.getTestFragment();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                detailsFragment.setItem(new PhotoItem("Hello world", "Fake content goes here",
                        android.support.v17.leanback.test.R.drawable.spiderman));
            }
        });

        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return detailsFragment.getRowsFragment().getVerticalGridView().getChildCount() > 0;
            }
        });
        final View firstRow = detailsFragment.getRowsFragment().getVerticalGridView().getChildAt(0);
        final int screenHeight = detailsFragment.getRowsFragment().getVerticalGridView()
                .getHeight();

        assertTrue(firstRow.hasFocus());
        assertTrue(detailsFragment.isShowingTitle());
        assertTrue(firstRow.getTop() > 0 && firstRow.getTop() < screenHeight);

        sendKeys(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(firstRow.hasFocus());

        SystemClock.sleep(1000);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        final MediaPlayerGlue glue = new MediaPlayerGlue(mActivity);
                        detailsFragment.mDetailsBackgroundController.setupVideoPlayback(glue);
                        glue.setMode(MediaPlayerGlue.REPEAT_ALL);
                        glue.setArtist("A Googleer");
                        glue.setTitle("Diving with Sharks");
                        glue.setMediaSource(Uri.parse(
                                "android.resource://android.support.v17.leanback.test/raw/video"));
                    }
                }
        );

        // after setup Video Playback the DPAD up will navigate to Video Fragment.
        sendKeys(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(detailsFragment.mVideoFragment.getView().hasFocus());
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return ((MediaPlayerGlue) detailsFragment.mDetailsBackgroundController
                        .getPlaybackGlue()).isMediaPlaying();
            }
        });
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return 0 == getCoverDrawableAlpha(detailsFragment.mDetailsBackgroundController);
            }
        });

        // wait a little bit to replace with new Glue
        SystemClock.sleep(1000);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        final MediaPlayerGlue glue2 = new MediaPlayerGlue(mActivity);
                        detailsFragment.mDetailsBackgroundController.setupVideoPlayback(glue2);
                        glue2.setMode(MediaPlayerGlue.REPEAT_ALL);
                        glue2.setArtist("A Googleer");
                        glue2.setTitle("Diving with Sharks");
                        glue2.setMediaSource(Uri.parse(
                                "android.resource://android.support.v17.leanback.test/raw/video"));
                    }
                }
        );

        // test switchToRows() and switchToVideo()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        detailsFragment.mDetailsBackgroundController.switchToRows();
                    }
                }
        );
        assertTrue(detailsFragment.mRowsFragment.getView().hasFocus());
        PollingCheck.waitFor(new PollingCheck.ViewStableOnScreen(firstRow));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        detailsFragment.mDetailsBackgroundController.switchToVideo();
                    }
                }
        );
        assertTrue(detailsFragment.mVideoFragment.getView().hasFocus());
        PollingCheck.waitFor(new PollingCheck.ViewStableOnScreen(firstRow));
    }

    @Test
    public void clearVideo() {
        launchAndWaitActivity(DetailsFragmentWithNoVideo.class, new Options().uiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);
        final DetailsFragmentWithNoVideo detailsFragment =
                (DetailsFragmentWithNoVideo) mActivity.getTestFragment();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                detailsFragment.setItem(new PhotoItem("Hello world", "Fake content goes here",
                        android.support.v17.leanback.test.R.drawable.spiderman));
            }
        });

        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return detailsFragment.getRowsFragment().getVerticalGridView().getChildCount() > 0;
            }
        });
        final View firstRow = detailsFragment.getRowsFragment().getVerticalGridView().getChildAt(0);
        final int screenHeight = detailsFragment.getRowsFragment().getVerticalGridView()
                .getHeight();

        assertTrue(firstRow.hasFocus());
        assertTrue(detailsFragment.isShowingTitle());
        assertTrue(firstRow.getTop() > 0 && firstRow.getTop() < screenHeight);

        SystemClock.sleep(1000);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        final MediaPlayerGlue glue = new MediaPlayerGlue(mActivity);
                        detailsFragment.mDetailsBackgroundController.setupVideoPlayback(glue);
                        glue.setMode(MediaPlayerGlue.REPEAT_ALL);
                        glue.setArtist("A Googleer");
                        glue.setTitle("Diving with Sharks");
                        glue.setMediaSource(Uri.parse(
                                "android.resource://android.support.v17.leanback.test/raw/video"));
                    }
                }
        );

        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return ((MediaPlayerGlue) detailsFragment.mDetailsBackgroundController
                        .getPlaybackGlue()).isMediaPlaying();
            }
        });
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return 0 == getCoverDrawableAlpha(detailsFragment.mDetailsBackgroundController);
            }
        });

        // wait a little bit then clear glue
        SystemClock.sleep(1000);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        detailsFragment.mDetailsBackgroundController.setupVideoPlayback(null);
                    }
                }
        );
        // background should fade in upon clear playback
        PollingCheck.waitFor(4000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return 255 == getCoverDrawableAlpha(detailsFragment.mDetailsBackgroundController);
            }
        });
    }

    public static class DetailsFragmentWithNoItem extends DetailsTestFragment {

        final DetailsFragmentBackgroundController mDetailsBackground =
                new DetailsFragmentBackgroundController(this);

        public DetailsFragmentWithNoItem() {
            mTimeToLoadOverviewRow = mTimeToLoadRelatedRow = 100;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDetailsBackground.enableParallax();
        }

        @Override
        public void onStart() {
            super.onStart();
            Bitmap bitmap = BitmapFactory.decodeResource(getActivity().getResources(),
                    android.support.v17.leanback.test.R.drawable.spiderman);
            mDetailsBackground.setCoverBitmap(bitmap);
        }

        @Override
        public void onStop() {
            mDetailsBackground.setCoverBitmap(null);
            super.onStop();
        }
    }

    @Test
    public void noInitialItem() {
        launchAndWaitActivity(DetailsFragmentWithNoItem.class, new Options().uiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN), 0);
        final DetailsFragmentWithNoItem detailsFragment =
                (DetailsFragmentWithNoItem) mActivity.getTestFragment();

        final int recyclerViewHeight = detailsFragment.getRowsFragment().getVerticalGridView()
                .getHeight();
        assertTrue(recyclerViewHeight > 0);

        assertEquals(255, getCoverDrawableAlpha(detailsFragment.mDetailsBackgroundController));
        assertEquals(255, detailsFragment.mDetailsBackgroundController.mParallaxDrawable
                .getAlpha());
        Drawable coverDrawable = detailsFragment.mDetailsBackgroundController.getCoverDrawable();
        assertEquals(0, coverDrawable.getBounds().top);
        assertEquals(recyclerViewHeight, coverDrawable.getBounds().bottom);
        Drawable bottomDrawable = detailsFragment.mDetailsBackgroundController.getBottomDrawable();
        assertEquals(recyclerViewHeight, bottomDrawable.getBounds().top);
        assertEquals(recyclerViewHeight, bottomDrawable.getBounds().bottom);
    }
}
