package com.buildbox.consent;

import com.breakbounce.gamezapp.ResData;

import java.util.ArrayList;
import java.util.List;

public class ConsentHelper {

    public static List<SdkConsentInfo> getSdkConsentInfos() {
        ArrayList<SdkConsentInfo> sdks = new ArrayList<>();
        /* admob */
        sdks.add( new SdkConsentInfo("admob", "Admob", "https://policies.google.com/technologies/partner-sites"));
        /* admob */
        //sdks.add( new SdkConsentInfo("{networkname}", "{DisplayName}", "{PrivacyPolicyUrl}"));
        /* ironsource */ /*
        sdks.add( new SdkConsentInfo("ironsource", "ironSource", "https://developers.ironsrc.com/ironsource-mobile/android/ironsource-mobile-privacy-policy/"));
        */ /* ironsource */
        /* applovin */ /*
        sdks.add( new SdkConsentInfo("applovin", "AppLovin", "https://www.applovin.com/privacy/"));
        */ /* applovin */
        /* adbox-applovin */
        /*sdks.add( new SdkConsentInfo("adbox-applovin", "AppLovin", "https://www.applovin.com/privacy/")); */
        /* adbox-applovin */
        /* adbox-vungle */
        /* sdks.add( new SdkConsentInfo("adbox-vungle", "Vungle", "https://vungle.com/privacy/")); */
        /* adbox-vungle */
        /* facebook-analytics */ /*
        sdks.add(new SdkConsentInfo("facebook-analytics", "Facebook Analytics", "https://www.facebook.com/about/privacy/"));
        */ /* facebook-analytics */
        /*sdks.add( new SdkConsentInfo(ResData.getAppName(), ResData.getAppName(), ResData.getPrivacyPolicyUrl()));*/
        return sdks;
    }

    public static String getConsentKey(String sdkId) {
        return sdkId + "_CONSENT_KEY";
    }
}
