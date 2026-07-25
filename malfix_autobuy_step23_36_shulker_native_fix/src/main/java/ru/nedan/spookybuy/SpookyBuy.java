package ru.nedan.spookybuy;

import ru.malfix.autobuy.MalfixAutoBuyMod;
import ru.malfix.autobuy.client.MalfixClientRuntime;
import ru.malfix.autobuy.render.PotatoMode;

/**
 * Compatibility bridge for old NeverBuy/SpookyBuy-style Nashorn scripts.
 * It does not implement the old autobuy. It only lets scripts pause/resume
 * Malfix automation safely while they move items in shulkers.
 */
public final class SpookyBuy {
    private static final SpookyBuy INSTANCE = new SpookyBuy();

    private SpookyBuy() {
    }

    public static SpookyBuy getInstance() {
        return INSTANCE;
    }

    public boolean isPotatoMode() {
        return PotatoMode.isEnabled();
    }

    public void setPotatoMode(boolean state) {
        PotatoMode.setEnabled(state);
    }

    public boolean isState() {
        MalfixClientRuntime runtime = MalfixAutoBuyMod.runtime();
        return runtime != null && runtime.isAutomationActiveForLegacyScript();
    }

    public void setState(boolean state) {
        MalfixClientRuntime runtime = MalfixAutoBuyMod.runtime();
        if (runtime != null) {
            runtime.setLegacyScriptPause(!state, "spookybuy_compat_setState_" + state);
        }
    }
}
