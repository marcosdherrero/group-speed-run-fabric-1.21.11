package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfig;

/**
 * Stores the current structure location data on the client.
 * This state is updated whenever a sync packet is received from the server.
 * The HUD uses these values to display the "Locate" markers.
 */
public class GSRLocateClientState {

    // Nether Fortress Data
    public static boolean fortressActive = false;
    public static int fortressX = 0, fortressZ = 0;

    // Bastion Remnant Data
    public static boolean bastionActive = false;
    public static int bastionX = 0, bastionZ = 0;

    // Stronghold Data
    public static boolean strongholdActive = false;
    public static int strongholdX = 0, strongholdZ = 0;

    // End Ship / City Data
    public static boolean shipActive = false;
    public static int shipX = 0, shipZ = 0;

    /**
     * Updates the client-side state using data from the main config.
     * Call this inside your ClientPlayNetworking receiver.
     * * @param config The config object received and parsed from the server's JSON.
     */
    public static void update(GSRConfig config) {
        if (config == null) return;

        // Sync Fortress
        fortressActive = config.fortressActive;
        fortressX = config.fortressX;
        fortressZ = config.fortressZ;

        // Sync Bastion
        bastionActive = config.bastionActive;
        bastionX = config.bastionX;
        bastionZ = config.bastionZ;

        // Sync Stronghold
        strongholdActive = config.strongholdActive;
        strongholdX = config.strongholdX;
        strongholdZ = config.strongholdZ;

        // Sync End Ship
        shipActive = config.shipActive;
        shipX = config.shipX;
        shipZ = config.shipZ;
    }
}