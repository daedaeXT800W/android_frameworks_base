/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.hardware.display.WifiDisplayStatus;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.internal.view.RotationPolicy;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.BrightnessController.BrightnessStateChangeCallback;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

import java.util.List;


class QuickSettingsModel implements BluetoothStateChangeCallback,
        NetworkSignalChangedCallback,
        BatteryStateChangeCallback,
        LocationGpsStateChangeCallback,
        BrightnessStateChangeCallback {

    /** Represents the state of a given attribute. */
    static class State {
        int iconId;
        String label;
        boolean enabled = false;
    }
    static class BatteryState extends State {
        int batteryLevel;
        boolean pluggedIn;
    }
    static class RSSIState extends State {
        int signalIconId;
        int dataTypeIconId;
    }
    static class UserState extends State {
        Drawable avatar;
    }
    static class BrightnessState extends State {
        boolean autoBrightness;
    }

    /** The callback to update a given tile. */
    interface RefreshCallback {
        public void refreshView(QuickSettingsTileView view, State state);
    }

    /** Broadcast receive to determine if there is an alarm set. */
    private BroadcastReceiver mAlarmIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_ALARM_CHANGED)) {
                onAlarmChanged(intent);
                onNextAlarmChanged();
            }
        }
    };

    /** ContentObserver to determine the next alarm */
    private class NextAlarmObserver extends ContentObserver {
        public NextAlarmObserver(Handler handler) {
            super(handler);
        }

        @Override public void onChange(boolean selfChange) {
            onNextAlarmChanged();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED), false, this);
        }
    }

    private Context mContext;
    private Handler mHandler;
    private NextAlarmObserver mNextAlarmObserver;

    private QuickSettingsTileView mUserTile;
    private RefreshCallback mUserCallback;
    private UserState mUserState = new UserState();

    private QuickSettingsTileView mTimeTile;
    private RefreshCallback mTimeAlarmCallback;
    private State mTimeAlarmState = new State();

    private QuickSettingsTileView mAirplaneModeTile;
    private RefreshCallback mAirplaneModeCallback;
    private State mAirplaneModeState = new State();

    private QuickSettingsTileView mWifiTile;
    private RefreshCallback mWifiCallback;
    private State mWifiState = new State();

    private QuickSettingsTileView mWifiDisplayTile;
    private RefreshCallback mWifiDisplayCallback;
    private State mWifiDisplayState = new State();

    private QuickSettingsTileView mRSSITile;
    private RefreshCallback mRSSICallback;
    private RSSIState mRSSIState = new RSSIState();

    private QuickSettingsTileView mBluetoothTile;
    private RefreshCallback mBluetoothCallback;
    private State mBluetoothState = new State();

    private QuickSettingsTileView mBatteryTile;
    private RefreshCallback mBatteryCallback;
    private BatteryState mBatteryState = new BatteryState();

    private QuickSettingsTileView mLocationTile;
    private RefreshCallback mLocationCallback;
    private State mLocationState = new State();

    private QuickSettingsTileView mImeTile;
    private RefreshCallback mImeCallback;
    private State mImeState = new State();

    private QuickSettingsTileView mRotationLockTile;
    private RefreshCallback mRotationLockCallback;
    private State mRotationLockState = new State();

    private QuickSettingsTileView mBrightnessTile;
    private RefreshCallback mBrightnessCallback;
    private BrightnessState mBrightnessState = new BrightnessState();

    public QuickSettingsModel(Context context) {
        mContext = context;
        mHandler = new Handler();
        mNextAlarmObserver = new NextAlarmObserver(mHandler);
        mNextAlarmObserver.startObserving();

        IntentFilter alarmIntentFilter = new IntentFilter();
        alarmIntentFilter.addAction(Intent.ACTION_ALARM_CHANGED);
        context.registerReceiver(mAlarmIntentReceiver, alarmIntentFilter);
    }

    // User
    void addUserTile(QuickSettingsTileView view, RefreshCallback cb) {
        mUserTile = view;
        mUserCallback = cb;
        mUserCallback.refreshView(mUserTile, mUserState);
    }
    void setUserTileInfo(String name, Drawable avatar) {
        mUserState.label = name;
        mUserState.avatar = avatar;
        mUserCallback.refreshView(mUserTile, mUserState);
    }

    // Time
    void addTimeTile(QuickSettingsTileView view, RefreshCallback cb) {
        mTimeTile = view;
        mTimeAlarmCallback = cb;
        mTimeAlarmCallback.refreshView(view, mTimeAlarmState);
    }
    void onAlarmChanged(Intent intent) {
        mTimeAlarmState.enabled = intent.getBooleanExtra("alarmSet", false);
        mTimeAlarmCallback.refreshView(mTimeTile, mTimeAlarmState);
    }
    void onNextAlarmChanged() {
        mTimeAlarmState.label = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED);
        mTimeAlarmCallback.refreshView(mTimeTile, mTimeAlarmState);
    }

    // Airplane Mode
    void addAirplaneModeTile(QuickSettingsTileView view, RefreshCallback cb) {
        mAirplaneModeTile = view;
        mAirplaneModeTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAirplaneModeState.enabled) {
                    setAirplaneModeState(false);
                } else {
                    setAirplaneModeState(true);
                }
            }
        });
        mAirplaneModeCallback = cb;
        mAirplaneModeCallback.refreshView(mAirplaneModeTile, mAirplaneModeState);
    }
    private void setAirplaneModeState(boolean enabled) {
        // TODO: Sets the view to be "awaiting" if not already awaiting

        // Change the system setting
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON,
                                enabled ? 1 : 0);

        // Post the intent
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enabled);
        mContext.sendBroadcast(intent);
    }
    // NetworkSignalChanged callback
    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        // TODO: If view is in awaiting state, disable
        Resources r = mContext.getResources();
        mAirplaneModeState.enabled = enabled;
        mAirplaneModeState.iconId = (enabled ?
                R.drawable.ic_qs_airplane_on :
                R.drawable.ic_qs_airplane_off);
        mAirplaneModeCallback.refreshView(mAirplaneModeTile, mAirplaneModeState);
    }

    // Wifi
    void addWifiTile(QuickSettingsTileView view, RefreshCallback cb) {
        mWifiTile = view;
        mWifiCallback = cb;
        mWifiCallback.refreshView(mWifiTile, mWifiState);
    }
    // NetworkSignalChanged callback
    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId, String enabledDesc) {
        // TODO: If view is in awaiting state, disable
        Resources r = mContext.getResources();
        mWifiState.iconId = enabled && (wifiSignalIconId > 0)
                ? wifiSignalIconId
                : R.drawable.ic_qs_wifi_no_network;
        mWifiState.label = enabled
                ? enabledDesc
                : r.getString(R.string.quick_settings_wifi_no_network);
        mWifiCallback.refreshView(mWifiTile, mWifiState);
    }

    // RSSI
    void addRSSITile(QuickSettingsTileView view, RefreshCallback cb) {
        mRSSITile = view;
        mRSSICallback = cb;
        mRSSICallback.refreshView(mRSSITile, mRSSIState);
    }
    boolean deviceSupportsTelephony() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }
    // NetworkSignalChanged callback
    @Override
    public void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
            int dataTypeIconId, String enabledDesc) {
        if (deviceSupportsTelephony()) {
            // TODO: If view is in awaiting state, disable
            Resources r = mContext.getResources();
            mRSSIState.signalIconId = enabled && (mobileSignalIconId > 0)
                    ? mobileSignalIconId
                    : R.drawable.ic_qs_signal_no_signal;
            mRSSIState.dataTypeIconId = enabled && (dataTypeIconId > 0)
                    ? dataTypeIconId
                    : 0;
            mRSSIState.label = enabled
                    ? enabledDesc
                    : r.getString(R.string.quick_settings_rssi_emergency_only);
            mRSSICallback.refreshView(mRSSITile, mRSSIState);
        }
    }

    // Bluetooth
    void addBluetoothTile(QuickSettingsTileView view, RefreshCallback cb) {
        mBluetoothTile = view;
        mBluetoothCallback = cb;

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        onBluetoothStateChange(adapter.isEnabled());
    }
    boolean deviceSupportsBluetooth() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }
    // BluetoothController callback
    @Override
    public void onBluetoothStateChange(boolean on) {
        // TODO: If view is in awaiting state, disable
        Resources r = mContext.getResources();
        mBluetoothState.enabled = on;
        if (on) {
            mBluetoothState.iconId = R.drawable.ic_qs_bluetooth_on;
        } else {
            mBluetoothState.iconId = R.drawable.ic_qs_bluetooth_off;
        }
        mBluetoothCallback.refreshView(mBluetoothTile, mBluetoothState);
    }

    // Battery
    void addBatteryTile(QuickSettingsTileView view, RefreshCallback cb) {
        mBatteryTile = view;
        mBatteryCallback = cb;
        mBatteryCallback.refreshView(mBatteryTile, mBatteryState);
    }
    // BatteryController callback
    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        mBatteryState.batteryLevel = level;
        mBatteryState.pluggedIn = pluggedIn;
        mBatteryCallback.refreshView(mBatteryTile, mBatteryState);
    }

    // Location
    void addLocationTile(QuickSettingsTileView view, RefreshCallback cb) {
        mLocationTile = view;
        mLocationCallback = cb;
        mLocationCallback.refreshView(mLocationTile, mLocationState);
    }
    // LocationController callback
    @Override
    public void onLocationGpsStateChanged(boolean inUse, String description) {
        mLocationState.enabled = inUse;
        mLocationState.label = description;
        mLocationCallback.refreshView(mLocationTile, mLocationState);
    }

    // Wifi Display
    void addWifiDisplayTile(QuickSettingsTileView view, RefreshCallback cb) {
        mWifiDisplayTile = view;
        mWifiDisplayCallback = cb;
    }
    public void onWifiDisplayStateChanged(WifiDisplayStatus status) {
        mWifiDisplayState.enabled = status.isEnabled();
        if (status.getActiveDisplay() != null) {
            mWifiDisplayState.label = status.getActiveDisplay().getDeviceName();
        } else {
            mWifiDisplayState.label = mContext.getString(
                    R.string.quick_settings_wifi_display_no_connection_label);
        }
        mWifiDisplayCallback.refreshView(mWifiDisplayTile, mWifiDisplayState);

    }

    // IME
    void addImeTile(QuickSettingsTileView view, RefreshCallback cb) {
        mImeTile = view;
        mImeCallback = cb;
        mImeCallback.refreshView(mImeTile, mImeState);
    }
    void onImeWindowStatusChanged(boolean visible) {
        InputMethodManager imm =
                (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> imis = imm.getInputMethodList();

        mImeState.enabled = visible;
        mImeState.label = getCurrentInputMethodName(mContext, mContext.getContentResolver(),
                imm, imis, mContext.getPackageManager());
        mImeCallback.refreshView(mImeTile, mImeState);
    }
    private static String getCurrentInputMethodName(Context context, ContentResolver resolver,
            InputMethodManager imm, List<InputMethodInfo> imis, PackageManager pm) {
        if (resolver == null || imis == null) return null;
        final String currentInputMethodId = Settings.Secure.getString(resolver,
                Settings.Secure.DEFAULT_INPUT_METHOD);
        if (TextUtils.isEmpty(currentInputMethodId)) return null;
        for (InputMethodInfo imi : imis) {
            if (currentInputMethodId.equals(imi.getId())) {
                final InputMethodSubtype subtype = imm.getCurrentInputMethodSubtype();
                final CharSequence summary = subtype != null
                        ? subtype.getDisplayName(context, imi.getPackageName(),
                                imi.getServiceInfo().applicationInfo)
                        : context.getString(R.string.quick_settings_ime_label);
                return summary.toString();
            }
        }
        return null;
    }

    // Rotation lock
    void addRotationLockTile(QuickSettingsTileView view, RefreshCallback cb) {
        mRotationLockTile = view;
        mRotationLockCallback = cb;
        onRotationLockChanged();
    }
    void onRotationLockChanged() {
        boolean locked = RotationPolicy.isRotationLocked(mContext);
        mRotationLockState.enabled = locked;
        mRotationLockState.iconId = locked
                ? R.drawable.ic_qs_rotation_locked
                : R.drawable.ic_qs_auto_rotate;
        mRotationLockState.label = locked
                ? mContext.getString(R.string.quick_settings_rotation_locked_label)
                : mContext.getString(R.string.quick_settings_rotation_unlocked_label);

        // may be called before addRotationLockTile due to RotationPolicyListener in QuickSettings 
        if (mRotationLockTile != null && mRotationLockCallback != null) {
            mRotationLockCallback.refreshView(mRotationLockTile, mRotationLockState);
        }
    }

    // Brightness
    void addBrightnessTile(QuickSettingsTileView view, RefreshCallback cb) {
        mBrightnessTile = view;
        mBrightnessCallback = cb;
        onBrightnessLevelChanged();
    }
    @Override
    public void onBrightnessLevelChanged() {
        int mode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        mBrightnessState.autoBrightness =
                (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        mBrightnessState.iconId = mBrightnessState.autoBrightness
                ? R.drawable.ic_qs_brightness_auto_on
                : R.drawable.ic_qs_brightness_auto_off;
        mBrightnessCallback.refreshView(mBrightnessTile, mBrightnessState);
    }

}