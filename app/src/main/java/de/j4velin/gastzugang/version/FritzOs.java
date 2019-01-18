package de.j4velin.gastzugang.version;

import java.io.IOException;

import de.j4velin.gastzugang.WiFiData;

public interface FritzOs {
    public WiFiData readConfig(final String FRITZBOX_ADDRESS, final String SID) throws IOException;

    public boolean setConfig(final String FRITZBOX_ADDRESS, final String SID,
                             final WiFiData newConfig);

    public int getVersion();
}
