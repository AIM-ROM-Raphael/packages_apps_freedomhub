/*
 * Copyright (C) 2020 AIMROM
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

package com.aim.freedomhub.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.aim.Utils;
import com.android.internal.util.aim.PackageUtils;
import com.aim.freedomhub.preferences.SystemSettingMasterSwitchPreference;

import com.aim.freedomhub.fragments.ImeSettings;
import com.aim.freedomhub.fragments.GamingMode;
import com.aim.freedomhub.fragments.SmartCharging;

public class Misc extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String SHOW_CPU_INFO_KEY = "show_cpu_info";
    private static final String FLASHLIGHT_ON_CALL = "flashlight_on_call";
    private static final String PREF_STOCK_RECENTS_CATEGORY = "stock_recents_category";
    private static final String PREF_ALTERNATIVE_RECENTS_CATEGORY = "alternative_recents_category";
    private static final String PREF_SWIPE_UP_ENABLED = "swipe_up_enabled_warning";
    private static final String NAVIGATION_BAR_RECENTS_STYLE = "navbar_recents_style";
    private static final String SMART_CHARGING = "smart_charging";

    private SwitchPreference mShowCpuInfo;
    private ListPreference mFlashlightOnCall;
    private PreferenceCategory mStockRecentsCategory;
    private PreferenceCategory mAlternativeRecentsCategory;
    private Context mContext;
    private ListPreference mNavbarRecentsStyle;
    private Preference mSmartCharging;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.freedomhub_misc);
        PreferenceScreen prefScreen = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();
        Context mContext = getActivity().getApplicationContext();
        final Resources res = getResources();

        mShowCpuInfo = (SwitchPreference) prefScreen.findPreference(SHOW_CPU_INFO_KEY);
        mShowCpuInfo.setChecked(Settings.Global.getInt(resolver,
                Settings.Global.SHOW_CPU_OVERLAY, 0) == 1);
        mShowCpuInfo.setOnPreferenceChangeListener(this);

        mFlashlightOnCall = (ListPreference) findPreference(FLASHLIGHT_ON_CALL);
        if (!Utils.deviceSupportsFlashLight(mContext))
            prefScreen.removePreference(mFlashlightOnCall);

        mSmartCharging = (Preference) prefScreen.findPreference(SMART_CHARGING);
        boolean mSmartChargingSupported = res.getBoolean(
                com.android.internal.R.bool.config_smartChargingAvailable);
        if (!mSmartChargingSupported)
            prefScreen.removePreference(mSmartCharging);

        mNavbarRecentsStyle = (ListPreference) findPreference(NAVIGATION_BAR_RECENTS_STYLE);
        int recentsStyle = Settings.System.getInt(resolver,
                Settings.System.OMNI_NAVIGATION_BAR_RECENTS, 0);

        mNavbarRecentsStyle.setValue(Integer.toString(recentsStyle));
        mNavbarRecentsStyle.setSummary(mNavbarRecentsStyle.getEntry());
        mNavbarRecentsStyle.setOnPreferenceChangeListener(this);

        mStockRecentsCategory = (PreferenceCategory) findPreference(PREF_STOCK_RECENTS_CATEGORY);
        mAlternativeRecentsCategory =
                (PreferenceCategory) findPreference(PREF_ALTERNATIVE_RECENTS_CATEGORY);

        // Alternative recents en-/disabling
        Preference.OnPreferenceChangeListener alternativeRecentsChangeListener =
                new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateDependencies(preference, (Boolean) newValue);
                return true;
            }
        };
        for (int i = 0; i < mAlternativeRecentsCategory.getPreferenceCount(); i++) {
            Preference preference = mAlternativeRecentsCategory.getPreference(i);
            if (preference instanceof SystemSettingMasterSwitchPreference) {
                preference.setOnPreferenceChangeListener(alternativeRecentsChangeListener);
            }
        }

        updateDependencies();

        // Warning for alternative recents when gesture navigation is enabled,
        // which directly controls quickstep (launcher) recents.
        final int navigationMode = getActivity().getResources()
                .getInteger(com.android.internal.R.integer.config_navBarInteractionMode);
        // config_navBarInteractionMode:
        // 0: 3 button mode (supports slim recents)
        // 1: 2 button mode (currently does not support alternative recents)
        // 2: gesture only (currently does not support alternative recents)
        if (navigationMode != 0) {
            for (int i = 0; i < mAlternativeRecentsCategory.getPreferenceCount(); i++) {
                Preference preference = mAlternativeRecentsCategory.getPreference(i);
                if (PREF_SWIPE_UP_ENABLED.equals(preference.getKey())) {
                    // We want to have that one enabled
                    continue;
                }
                preference.setEnabled(false);
            }
        } else {
            mAlternativeRecentsCategory.removePreference(findPreference(PREF_SWIPE_UP_ENABLED));
        }
    }

    private void updateDependencies() {
        updateDependencies(null, null);
    }

    private void updateDependencies(Preference updatedPreference, Boolean newValue) {
        // Disable stock recents category if alternative enabled
        boolean alternativeRecentsEnabled = newValue != null && newValue;
        if (!alternativeRecentsEnabled) {
            for (int i = 0; i < mAlternativeRecentsCategory.getPreferenceCount(); i++) {
                Preference preference = mAlternativeRecentsCategory.getPreference(i);
                if (preference == updatedPreference) {
                    // Already used newValue
                    continue;
                }
                if (preference instanceof SystemSettingMasterSwitchPreference
                        && ((SystemSettingMasterSwitchPreference) preference).isChecked()) {
                    alternativeRecentsEnabled = true;
                    break;
                }
            }
        }
        if (mStockRecentsCategory != null) {
            mStockRecentsCategory.setEnabled(!alternativeRecentsEnabled);
        }
    }

    private static void writeCpuInfoOptions(Context mContext, boolean value) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.SHOW_CPU_OVERLAY, value ? 1 : 0);
        Intent service = (new Intent())
                .setClassName("com.android.systemui", "com.android.systemui.CPUInfoService");
        if (value) {
            mContext.startService(service);
        } else {
            mContext.stopService(service);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.FREEDOMHUB;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.FLASHLIGHT_ON_CALL, 0, UserHandle.USER_CURRENT);
        writeCpuInfoOptions(mContext, false);
        ImeSettings.reset(mContext);
        GamingMode.reset(mContext);
        SmartCharging.reset(mContext);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        Context mContext = getActivity().getApplicationContext();
        if (preference == mShowCpuInfo) {
            writeCpuInfoOptions(mContext, (Boolean) newValue);
            return true;
        } else if (preference == mNavbarRecentsStyle) {
            int value = Integer.valueOf((String) newValue);
            if (value == 1) {
                if (!isOmniSwitchInstalled()){
                    doOmniSwitchUnavail();
                } else if (!Utils.isOmniSwitchRunning(getActivity())) {
                    doOmniSwitchConfig();
                }
            }
            int index = mNavbarRecentsStyle.findIndexOfValue((String) newValue);
            mNavbarRecentsStyle.setSummary(mNavbarRecentsStyle.getEntries()[index]);
            Settings.System.putInt(getContentResolver(), Settings.System.OMNI_NAVIGATION_BAR_RECENTS, value);
            return true;
        }
        return false;
    }

    private void checkForOmniSwitchRecents() {
        if (!isOmniSwitchInstalled()){
            doOmniSwitchUnavail();
        } else if (!Utils.isOmniSwitchRunning(getActivity())) {
            doOmniSwitchConfig();
        }
    }

    private void doOmniSwitchConfig() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setTitle(R.string.omniswitch_title);
        alertDialogBuilder.setMessage(R.string.omniswitch_dialog_running_new)
            .setPositiveButton(R.string.omniswitch_settings, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    startActivity(Utils.INTENT_LAUNCH_APP);
                }
            });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void doOmniSwitchUnavail() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setTitle(R.string.omniswitch_title);
        alertDialogBuilder.setMessage(R.string.omniswitch_dialog_unavail);
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private boolean isOmniSwitchInstalled() {
        return PackageUtils.isAvailableApp(Utils.APP_PACKAGE_NAME, getActivity());
    }
}

