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

package androidx.car.app.hardware.common;

import androidx.annotation.NonNull;
import androidx.car.app.annotations.CarProtocol;
import androidx.car.app.annotations.RequiresCarApi;

/**
 * A listener for data being returned about from the car hardware.
 *
 * @param <T> data type returned by the listener
 */
@CarProtocol
@RequiresCarApi(3)
public interface OnCarDataAvailableListener<T> {
    /**
     * Notifies that the requested data is available.
     *
     * @param data car hardware data that was requested.
     */
    void onCarDataAvailable(@NonNull T data);
}