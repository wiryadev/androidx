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
package androidx.appsearch.localstorage;

import androidx.annotation.RestrictTo;
import androidx.appsearch.app.Capabilities;

/**
 * An implementation of {@link Capabilities}. This implementation always returns true. This is
 * sufficient for the use in the local backend because all features are always available on the
 * local backend.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AlwaysSupportedCapabilities implements Capabilities {

    @Override
    public boolean isSubmatchSupported() {
        return true;
    }
}
