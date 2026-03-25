package com.blackaby.Backend.Platform;

/**
 * Describes which host features a backend supports.
 *
 * @param supportsPaletteConfiguration whether palette editing is available
 * @param supportsBootRomConfiguration whether boot ROM management is available
 * @param supportsSaveDataManagement whether battery save management is available
 * @param supportsSaveStates whether save-state management is available
 * @param supportsPatchLoading whether external patch loading is available
 */
public record EmulatorCapabilities(
        boolean supportsPaletteConfiguration,
        boolean supportsBootRomConfiguration,
        boolean supportsSaveDataManagement,
        boolean supportsSaveStates,
        boolean supportsPatchLoading) {
}
