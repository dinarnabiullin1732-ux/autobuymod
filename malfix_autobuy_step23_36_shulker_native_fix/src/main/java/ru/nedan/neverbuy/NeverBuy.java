package ru.nedan.neverbuy;

import ru.nedan.spookybuy.SpookyBuy;

/** Minimal compatibility facade for NeverBuy scripts. */
public final class NeverBuy {
    private static final NeverBuy INSTANCE = new NeverBuy();
    private NeverBuy() {}
    public static NeverBuy getInstance() { return INSTANCE; }
    public boolean isState() { return SpookyBuy.getInstance().isState(); }
    public void setState(boolean state) { SpookyBuy.getInstance().setState(state); }
}
