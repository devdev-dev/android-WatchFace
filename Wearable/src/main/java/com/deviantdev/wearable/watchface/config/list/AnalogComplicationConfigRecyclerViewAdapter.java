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

package com.deviantdev.wearable.watchface.config.list;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.support.wearable.complications.ProviderInfoRetriever.OnProviderInfoReceivedCallback;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.deviantdev.wearable.watchface.R;
import com.deviantdev.wearable.watchface.WatchFaceComplicationConfiguration.Complication;
import com.deviantdev.wearable.watchface.WatchFacePreferences;
import com.deviantdev.wearable.watchface.WatchFaceService;
import com.deviantdev.wearable.watchface.config.color.ColorSelectionActivity;
import com.deviantdev.wearable.watchface.model.AnalogComplicationConfigData.BackgroundComplicationConfigItem;
import com.deviantdev.wearable.watchface.model.AnalogComplicationConfigData.ColorConfigItem;
import com.deviantdev.wearable.watchface.model.AnalogComplicationConfigData.ConfigItemType;
import com.deviantdev.wearable.watchface.model.AnalogComplicationConfigData.MoreOptionsConfigItem;
import com.deviantdev.wearable.watchface.model.AnalogComplicationConfigData.PreviewAndComplicationsConfigItem;
import com.deviantdev.wearable.watchface.model.AnalogComplicationConfigData.UnreadNotificationConfigItem;

import java.util.ArrayList;
import java.util.concurrent.Executors;

/**
 * Displays different layouts for configuring watch face's complications and appearance settings
 * (highlight color [second arm], background color, unread notifications, etc.).
 * <p>
 * <p>All appearance settings are saved via {@link SharedPreferences}.
 * <p>
 * <p>Layouts provided by this adapter are split into 5 main view types.
 * <p>
 * <p>A watch face preview including complications. Allows user to tap on the complications to
 * change the complication data and see a live preview of the watch face.
 * <p>
 * <p>Simple arrow to indicate there are more options below the fold.
 * <p>
 * <p>Color configuration options for both highlight (seconds hand) and background color.
 * <p>
 * <p>Toggle for unread notifications.
 * <p>
 * <p>Background image complication configuration for changing background image of watch face.
 */
public class AnalogComplicationConfigRecyclerViewAdapter extends
        RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG = 0;
    public static final int TYPE_MORE_OPTIONS = 1;
    public static final int TYPE_COLOR_CONFIG = 2;
    public static final int TYPE_UNREAD_NOTIFICATION_CONFIG = 3;
    public static final int TYPE_BACKGROUND_COMPLICATION_IMAGE_CONFIG = 4;

    private static final String TAG = "CompConfigAdapter";

    private final WatchFacePreferences watchFacePreferences;

    // ComponentName associated with watch face service (service that renders watch face). Used
    // to retrieve complication information.
    private ComponentName mWatchFaceComponentName;

    private ArrayList<ConfigItemType> mSettingsDataSet;

    private Context mContext;

    // Selected complication id by user.
    private int mSelectedComplicationId;

    // Required to retrieve complication data from watch face for preview.
    private ProviderInfoRetriever mProviderInfoRetriever;

    // Maintains reference view holder to dynamically update watch face preview. Used instead of
    // notifyItemChanged(int position) to avoid flicker and re-inflating the view.
    private PreviewAndComplicationsViewHolder mPreviewAndComplicationsViewHolder;

    AnalogComplicationConfigRecyclerViewAdapter(Context context, Class watchFaceServiceClass,
            ArrayList<ConfigItemType> settingsDataSet) {

        mContext = context;

        mWatchFaceComponentName = new ComponentName(mContext, watchFaceServiceClass);

        watchFacePreferences = new WatchFacePreferences(mContext);
        watchFacePreferences.reloadSavedPreferences(context);

        mSettingsDataSet = settingsDataSet;

        // Default value is invalid (only changed when user taps to change complication).
        mSelectedComplicationId = -1;

        // Initialization of code to retrieve active complication data for the watch face.
        mProviderInfoRetriever = new ProviderInfoRetriever(mContext,
                Executors.newCachedThreadPool());
        mProviderInfoRetriever.init();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder(): viewType: " + viewType);

        RecyclerView.ViewHolder viewHolder = null;

        switch (viewType) {
            case TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG:
                // Need direct reference to watch face preview view holder to update watch face
                // preview based on selections from the user.
                mPreviewAndComplicationsViewHolder = new PreviewAndComplicationsViewHolder(
                        LayoutInflater.from(parent.getContext()).inflate(
                                R.layout.config_list_preview_and_complications_item, parent,
                                false));
                viewHolder = mPreviewAndComplicationsViewHolder;
                break;

            case TYPE_MORE_OPTIONS:
                viewHolder = new MoreOptionsViewHolder(
                        LayoutInflater.from(parent.getContext()).inflate(
                                R.layout.config_list_more_options_item, parent, false));
                break;

            case TYPE_COLOR_CONFIG:
                viewHolder = new ColorPickerViewHolder(
                        LayoutInflater.from(parent.getContext()).inflate(
                                R.layout.config_list_color_item, parent, false));
                break;

            case TYPE_UNREAD_NOTIFICATION_CONFIG:
                viewHolder = new UnreadNotificationViewHolder(
                        LayoutInflater.from(parent.getContext()).inflate(
                                R.layout.config_list_unread_notif_item, parent, false));
                break;

            case TYPE_BACKGROUND_COMPLICATION_IMAGE_CONFIG:
                viewHolder = new BackgroundComplicationViewHolder(
                        LayoutInflater.from(parent.getContext()).inflate(
                                R.layout.config_list_background_complication_item, parent, false));
                break;
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        Log.d(TAG, "Element " + position + " set.");

        // Pulls all data required for creating the UX for the specific setting option.
        ConfigItemType configItemType = mSettingsDataSet.get(position);

        switch (viewHolder.getItemViewType()) {
            case TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG:
                PreviewAndComplicationsViewHolder previewAndComplicationsViewHolder = (PreviewAndComplicationsViewHolder) viewHolder;

                PreviewAndComplicationsConfigItem previewAndComplicationsConfigItem = (PreviewAndComplicationsConfigItem) configItemType;

                int defaultComplicationResourceId = previewAndComplicationsConfigItem.getDefaultComplicationResourceId();
                previewAndComplicationsViewHolder.setDefaultComplicationDrawable(
                        defaultComplicationResourceId);

                previewAndComplicationsViewHolder.initializesColorsAndComplications();
                break;

            case TYPE_MORE_OPTIONS:
                MoreOptionsViewHolder moreOptionsViewHolder = (MoreOptionsViewHolder) viewHolder;
                MoreOptionsConfigItem moreOptionsConfigItem = (MoreOptionsConfigItem) configItemType;

                moreOptionsViewHolder.setIcon(moreOptionsConfigItem.getIconResourceId());
                break;

            case TYPE_COLOR_CONFIG:
                ColorPickerViewHolder colorPickerViewHolder = (ColorPickerViewHolder) viewHolder;
                ColorConfigItem colorConfigItem = (ColorConfigItem) configItemType;

                int iconResourceId = colorConfigItem.getIconResourceId();
                String name = colorConfigItem.getName();
                Class<ColorSelectionActivity> activity = colorConfigItem.getActivityToChoosePreference();

                colorPickerViewHolder.setIcon(iconResourceId);
                colorPickerViewHolder.setName(name);
                colorPickerViewHolder.setLaunchActivityToSelectColor(activity);
                break;

            case TYPE_UNREAD_NOTIFICATION_CONFIG:
                UnreadNotificationViewHolder unreadViewHolder = (UnreadNotificationViewHolder) viewHolder;

                UnreadNotificationConfigItem unreadConfigItem = (UnreadNotificationConfigItem) configItemType;

                int unreadEnabledIconResourceId = unreadConfigItem.getIconEnabledResourceId();
                int unreadDisabledIconResourceId = unreadConfigItem.getIconDisabledResourceId();

                String unreadName = unreadConfigItem.getName();

                unreadViewHolder.setIcons(unreadEnabledIconResourceId,
                        unreadDisabledIconResourceId);
                unreadViewHolder.setName(unreadName);
                break;

            case TYPE_BACKGROUND_COMPLICATION_IMAGE_CONFIG:
                BackgroundComplicationViewHolder backgroundComplicationViewHolder = (BackgroundComplicationViewHolder) viewHolder;

                BackgroundComplicationConfigItem backgroundComplicationConfigItem = (BackgroundComplicationConfigItem) configItemType;

                int backgroundIconResourceId = backgroundComplicationConfigItem.getIconResourceId();
                String backgroundName = backgroundComplicationConfigItem.getName();

                backgroundComplicationViewHolder.setIcon(backgroundIconResourceId);
                backgroundComplicationViewHolder.setName(backgroundName);
                break;
        }
    }

    @Override
    public int getItemViewType(int position) {
        ConfigItemType configItemType = mSettingsDataSet.get(position);
        return configItemType.getConfigType();
    }

    @Override
    public int getItemCount() {
        return mSettingsDataSet.size();
    }

    /**
     * Updates the selected complication id saved earlier with the new information.
     */
    void updateSelectedComplication(ComplicationProviderInfo complicationProviderInfo) {

        Log.d(TAG, "updateSelectedComplication: " + mPreviewAndComplicationsViewHolder);

        // Checks if view is inflated and complication id is valid.
        if (mPreviewAndComplicationsViewHolder != null && mSelectedComplicationId >= 0) {
            mPreviewAndComplicationsViewHolder.updateComplicationViews(mSelectedComplicationId,
                    complicationProviderInfo);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        // Required to release retriever for active complication data on detach.
        mProviderInfoRetriever.release();
    }

    public void updatePreviewColors() {
        Log.d(TAG, "updatePreviewColors(): " + mPreviewAndComplicationsViewHolder);

        if (mPreviewAndComplicationsViewHolder != null) {
            mPreviewAndComplicationsViewHolder.updateWatchFaceColors();
        }
    }

    /**
     * Displays watch face preview along with complication locations. Allows user to tap on the
     * complication they want to change and preview updates dynamically.
     */
    public class PreviewAndComplicationsViewHolder extends RecyclerView.ViewHolder implements
            OnClickListener {

        private View mWatchFaceArmsAndTicksView;
        private View mWatchFaceHighlightPreviewView;
        private ImageView mWatchFaceBackgroundPreviewImageView;

        private ImageView mLeftComplicationBackground;
        private ImageView mRightComplicationBackground;

        private ImageButton mLeftComplication;
        private ImageButton mRightComplication;

        private Drawable mDefaultComplicationDrawable;

        private boolean mBackgroundComplicationEnabled;

        private PreviewAndComplicationsViewHolder(final View view) {
            super(view);

            mWatchFaceBackgroundPreviewImageView = view.findViewById(R.id.watch_face_background);
            mWatchFaceArmsAndTicksView = view.findViewById(R.id.watch_face_arms_and_ticks);

            // In our case, just the second arm.
            mWatchFaceHighlightPreviewView = view.findViewById(R.id.watch_face_highlight);

            // Sets up left complication preview.
            mLeftComplicationBackground = view.findViewById(R.id.left_complication_background);
            mLeftComplication = view.findViewById(R.id.left_complication);
            mLeftComplication.setOnClickListener(this);

            // Sets up right complication preview.
            mRightComplicationBackground = view.findViewById(R.id.right_complication_background);
            mRightComplication = view.findViewById(R.id.right_complication);
            mRightComplication.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (view.equals(mLeftComplication)) {
                Log.d(TAG, "Left Complication click()");

                Activity currentActivity = (Activity) view.getContext();
                launchComplicationHelperActivity(currentActivity, Complication.LEFT);

            } else if (view.equals(mRightComplication)) {
                Log.d(TAG, "Right Complication click()");

                Activity currentActivity = (Activity) view.getContext();
                launchComplicationHelperActivity(currentActivity, Complication.RIGHT);
            }
        }

        private void updateWatchFaceColors() {

            // Only update background colors for preview if background complications are disabled.
            if (!mBackgroundComplicationEnabled) {

                PorterDuffColorFilter backgroundColorFilter = new PorterDuffColorFilter(
                        watchFacePreferences.getBackgroundColor(), PorterDuff.Mode.SRC_ATOP);

                mWatchFaceBackgroundPreviewImageView.getBackground().setColorFilter(
                        backgroundColorFilter);

            } else {
                // Inform user that they need to disable background image for color to work.
                CharSequence text = "Selected image overrides background color.";
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(mContext, text, duration);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }

            // Updates highlight color (just second arm).
            PorterDuffColorFilter highlightColorFilter = new PorterDuffColorFilter(
                    watchFacePreferences.getWatchHandHighlightColor(), PorterDuff.Mode.SRC_ATOP);

            mWatchFaceHighlightPreviewView.getBackground().setColorFilter(highlightColorFilter);
        }

        // Verifies the watch face supports the complication location, then launches the helper
        // class, so user can choose their complication data provider.
        private void launchComplicationHelperActivity(Activity currentActivity,
                Complication complication) {

            mSelectedComplicationId = complication.getId();

            mBackgroundComplicationEnabled = false;

            ComponentName watchFace = new ComponentName(currentActivity, WatchFaceService.class);

            currentActivity.startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(currentActivity,
                            watchFace, complication.getId(), complication.getSupportedTypes()),
                    AnalogComplicationConfigActivity.Companion.getCOMPLICATION_CONFIG_REQUEST_CODE());

        }

        private void setDefaultComplicationDrawable(int resourceId) {
            Context context = mWatchFaceArmsAndTicksView.getContext();
            mDefaultComplicationDrawable = context.getDrawable(resourceId);

            mLeftComplication.setImageDrawable(mDefaultComplicationDrawable);
            mLeftComplicationBackground.setVisibility(View.INVISIBLE);

            mRightComplication.setImageDrawable(mDefaultComplicationDrawable);
            mRightComplicationBackground.setVisibility(View.INVISIBLE);
        }

        private void updateComplicationViews(int watchFaceComplicationId,
                ComplicationProviderInfo complicationProviderInfo) {
            Log.d(TAG, "updateComplicationViews(): id: " + watchFaceComplicationId);
            Log.d(TAG, "\tinfo: " + complicationProviderInfo);

            if (watchFaceComplicationId == Complication.BACKGROUND.getId()) {
                if (complicationProviderInfo != null) {
                    mBackgroundComplicationEnabled = true;

                    // Since we can't get the background complication image outside of the
                    // watch face, we set the icon for that provider instead with a gray background.
                    PorterDuffColorFilter backgroundColorFilter = new PorterDuffColorFilter(
                            Color.GRAY, PorterDuff.Mode.SRC_ATOP);

                    mWatchFaceBackgroundPreviewImageView.getBackground().setColorFilter(
                            backgroundColorFilter);
                    mWatchFaceBackgroundPreviewImageView.setImageIcon(
                            complicationProviderInfo.providerIcon);

                } else {
                    mBackgroundComplicationEnabled = false;

                    // Clears icon for background if it was present before.
                    mWatchFaceBackgroundPreviewImageView.setImageResource(
                            android.R.color.transparent);

                    PorterDuffColorFilter backgroundColorFilter = new PorterDuffColorFilter(
                            watchFacePreferences.getBackgroundColor(), PorterDuff.Mode.SRC_ATOP);

                    mWatchFaceBackgroundPreviewImageView.getBackground().setColorFilter(
                            backgroundColorFilter);
                }

            } else if (watchFaceComplicationId == Complication.LEFT.getId()) {
                updateComplicationView(complicationProviderInfo, mLeftComplication,
                        mLeftComplicationBackground);

            } else if (watchFaceComplicationId == Complication.RIGHT.getId()) {
                updateComplicationView(complicationProviderInfo, mRightComplication,
                        mRightComplicationBackground);
            }
        }

        private void updateComplicationView(ComplicationProviderInfo complicationProviderInfo,
                ImageButton button, ImageView background) {
            if (complicationProviderInfo != null) {
                button.setImageIcon(complicationProviderInfo.providerIcon);
                button.setContentDescription(mContext.getString(R.string.edit_complication,
                        complicationProviderInfo.appName + " " + complicationProviderInfo.providerName));
                background.setVisibility(View.VISIBLE);
            } else {
                button.setImageDrawable(mDefaultComplicationDrawable);
                button.setContentDescription(mContext.getString(R.string.add_complication));
                background.setVisibility(View.INVISIBLE);
            }
        }

        private void initializesColorsAndComplications() {

            // Initializes highlight color (just second arm and part of complications).
            PorterDuffColorFilter highlightColorFilter = new PorterDuffColorFilter(
                    watchFacePreferences.getWatchHandHighlightColor(), PorterDuff.Mode.SRC_ATOP);

            mWatchFaceHighlightPreviewView.getBackground().setColorFilter(highlightColorFilter);

            // Initializes background color to gray (updates to color or complication icon based
            // on whether the background complication is live or not.
            PorterDuffColorFilter backgroundColorFilter = new PorterDuffColorFilter(Color.GRAY,
                    PorterDuff.Mode.SRC_ATOP);

            mWatchFaceBackgroundPreviewImageView.getBackground().setColorFilter(
                    backgroundColorFilter);

            mProviderInfoRetriever.retrieveProviderInfo(new OnProviderInfoReceivedCallback() {
                @Override
                public void onProviderInfoReceived(int watchFaceComplicationId,
                        @Nullable ComplicationProviderInfo complicationProviderInfo) {

                    Log.d(TAG, "onProviderInfoReceived: " + complicationProviderInfo);

                    updateComplicationViews(watchFaceComplicationId, complicationProviderInfo);
                }
            }, mWatchFaceComponentName, Complication.Companion.getAllIds());
        }
    }

}