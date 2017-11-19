/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.states;

import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_TRANSITION_MS;

import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Workspace;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * Definition for spring loaded state used during drag and drop.
 */
public class SpringLoadedState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_SHOW_SCRIM | FLAG_MULTI_PAGE |
            FLAG_DISABLE_ACCESSIBILITY | FLAG_DO_NOT_RESTORE;

    // Determines how long to wait after a rotation before restoring the screen orientation to
    // match the sensor state.
    private static final int RESTORE_SCREEN_ORIENTATION_DELAY = 500;

    public SpringLoadedState(int id) {
        super(id, ContainerType.OVERVIEW, SPRING_LOADED_TRANSITION_MS, 1f, STATE_FLAGS);
    }

    @Override
    public float[] getWorkspaceScaleAndTranslation(Launcher launcher) {
        DeviceProfile grid = launcher.getDeviceProfile();
        Workspace ws = launcher.getWorkspace();
        if (grid.isVerticalBarLayout() || ws.getChildCount() == 0) {
            return super.getWorkspaceScaleAndTranslation(launcher);
        }

        float scale = grid.workspaceSpringLoadShrinkFactor;
        Rect insets = launcher.getDragLayer().getInsets();

        float scaledHeight = scale * ws.getNormalChildHeight();
        float shrunkTop = insets.top + grid.dropTargetBarSizePx;
        float shrunkBottom = ws.getViewportHeight() - insets.bottom
                - grid.getWorkspacePadding(null).bottom
                - grid.workspaceSpringLoadedBottomSpace;
        float totalShrunkSpace = shrunkBottom - shrunkTop;

        float desiredCellTop = shrunkTop + (totalShrunkSpace - scaledHeight) / 2;

        float halfHeight = ws.getHeight() / 2;
        float myCenter = ws.getTop() + halfHeight;
        float cellTopFromCenter = halfHeight - ws.getChildAt(0).getTop();
        float actualCellTop = myCenter - cellTopFromCenter * scale;
        return new float[] { scale, (desiredCellTop - actualCellTop) / scale};
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        Workspace ws = launcher.getWorkspace();
        ws.showPageIndicatorAtCurrentScroll();
        ws.getPageIndicator().setShouldAutoHide(false);

        // Lock the orientation:
        if (launcher.isRotationEnabled()) {
            launcher.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        // Prevent any Un/InstallShortcutReceivers from updating the db while we are
        // in spring loaded mode
        InstallShortcutReceiver.enableInstallQueue(InstallShortcutReceiver.FLAG_DRAG_AND_DROP);
    }

    @Override
    public void onStateDisabled(final Launcher launcher) {
        launcher.getWorkspace().getPageIndicator().setShouldAutoHide(true);

        // Unlock rotation lock
        if (launcher.isRotationEnabled()) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    launcher.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }, RESTORE_SCREEN_ORIENTATION_DELAY);
        }

        // Re-enable any Un/InstallShortcutReceiver and now process any queued items
        InstallShortcutReceiver.disableAndFlushInstallQueue(
                InstallShortcutReceiver.FLAG_DRAG_AND_DROP, launcher);
    }

    @Override
    public View getFinalFocus(Launcher launcher) {
        return null;
    }
}