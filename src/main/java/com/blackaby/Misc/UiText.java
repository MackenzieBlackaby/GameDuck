package com.blackaby.Misc;

/**
 * Central catalog for user-facing desktop UI text.
 *
 * Should be easy to modify
 */
public final class UiText {

    private UiText() {
    }

    // ---------------------------------------------------------------------
    // Shared app-wide labels and status text
    // ---------------------------------------------------------------------
    public static final class Common {
        // App identity.
        public static final String APP_NAME = "GameDuck";

        // Shared status words.
        public static final String READY = "Ready";
        public static final String RUNNING = "Running";
        public static final String INSTALLED = "Installed";
        public static final String MISSING = "Missing";

        // Shared dialog and panel titles.
        public static final String WARNING_TITLE = "Warning";
        public static final String SEARCH_TITLE = "Search";
        public static final String CONSOLE_TITLE = "Console";
        public static final String STATUS = "Data Status";
        public static final String STATUS_ALL = "All games";
        public static final String STATUS_TRACKED = "Tracked games";

        // Shared console filter labels.
        public static final String CONSOLE_FILTER_ALL = "ALL";
        public static final String CONSOLE_FILTER_GB = "GB";
        public static final String CONSOLE_FILTER_GBC = "GBC";

        private Common() {
        }
    }

    // ---------------------------------------------------------------------
    // Core application windows
    // ---------------------------------------------------------------------
    public static final class AboutWindow {
        // Window chrome.
        public static final String WINDOW_TITLE = "About";
        public static final String VERSION = "Version 0.1";
        public static final String SUMMARY = "Nintendo Game Boy Emulator";
        public static final String AUTHOR = "By Mackenzie Blackaby";
        public static final String PROJECT_NOTE = "Final year project for Lancaster University";
        public static final String LEGAL_NOTE = "This emulator contains no IP or copyrighted material.";
        public static final String LICENSE_TITLE = "Mozilla Public License 2.0";
        public static final String LICENSE_HINT = "Open-source license included with GameDuck";
        public static final String LICENSE_LOAD_ERROR = "Unable to load the packaged license text.";

        private AboutWindow() {
        }
    }

    public static final class MainWindow {
        // Window chrome and top header copy.
        public static final String WINDOW_TITLE = Common.APP_NAME;
        public static final String HEADER_TITLE = Common.APP_NAME;
        public static final String HEADER_SUBTITLE = "Game Boy emulation suite";

        // Primary header action buttons.
        public static final String BUTTON_OPEN_ROM = "Open ROM";
        public static final String BUTTON_OPEN_ROM_HACK = "Open ROM Hack";
        public static final String BUTTON_LIBRARY = "Library";
        public static final String BUTTON_OPEN_IPS_PATCH = "Open IPS Patch";
        public static final String BUTTON_OPTIONS = "Options";

        // Display panel text.
        public static final String DISPLAY_TITLE = "Display";
        public static final String DISPLAY_HINT = "Game Output";
        public static final String DISPLAY_EMPTY_TITLE = "Ready to Play";
        public static final String DISPLAY_EMPTY_HELPER = "Open a ROM or browse your library to start a new session";

        // Serial panel text.
        public static final String SERIAL_TITLE = "Serial Output";
        public static final String SERIAL_HINT = "Live debug text from the emulated serial port";

        // Artwork panel titles and helper text.
        public static final String GAME_ART_TITLE = "Game Art";
        public static final String GAME_ART_HINT_DEFAULT = "Libretro box art";
        public static final String GAME_ART_LOAD_PROMPT = "Load a ROM to fetch artwork";
        public static final String GAME_ART_DISABLED_HINT = "(Disabled in window options)";

        // Main surface placeholder and loading text.
        public static final String NO_ROM_LOADED = "No ROM loaded";
        public static final String RETRIEVING_GAME_NAME = "Retrieving game name...";
        public static final String FETCHING_ARTWORK = "Fetching artwork...";
        public static final String NO_ARTWORK_FOUND = "No artwork found for this rom";
        public static final String NO_ARTWORK_HINT = "No matching libretro thumbnail was found for this ROM";

        // Game menu labels.
        public static final String GAME_MENU_TITLE = "Game";
        public static final String GAME_MENU_LIBRARY = "Library";
        public static final String GAME_MENU_OPEN_ROM = "Open ROM";
        public static final String GAME_MENU_OPEN_IPS_PATCH = "Open IPS Patch";
        public static final String GAME_MENU_LOAD_RECENT = "Load Recent";
        public static final String GAME_MENU_LOAD_RECENT_EMPTY = "No recent games";
        public static final String GAME_MENU_CLEAR_RECENT = "Clear Recent";
        public static final String GAME_MENU_PAUSE_GAME = "Pause Game";
        public static final String GAME_MENU_RESET_GAME = "Reset Game";
        public static final String GAME_MENU_CLOSE_GAME = "Close Game";
        public static final String GAME_MENU_CHEATS = "Cheats...";
        public static final String GAME_MENU_SAVE_STATE_MANAGER = "Save State Manager";
        public static final String GAME_MENU_QUICK_SAVE = "Quick Save";
        public static final String GAME_MENU_QUICK_LOAD = "Quick Load";
        public static final String GAME_MENU_SAVE_STATE = "Save State";
        public static final String GAME_MENU_LOAD_STATE = "Load State";
        public static final String GAME_MENU_QUICK_SLOT = "Quick Slot";
        // Top-level menu bar layout.
        public static final String[][] MENU_ITEMS = {
                { "File", "Options", "", "Exit", "" },
                { GAME_MENU_TITLE, GAME_MENU_LIBRARY, GAME_MENU_OPEN_ROM, GAME_MENU_OPEN_IPS_PATCH, "",
                        GAME_MENU_PAUSE_GAME, GAME_MENU_RESET_GAME, GAME_MENU_CLOSE_GAME, "",
                        GAME_MENU_QUICK_SAVE, GAME_MENU_QUICK_LOAD,
                        "" },
                { "View", "Enter Full View", "Toggle Fullscreen", "Toggle Maximise" },
                { "Help", "About", "", }
        };

        private MainWindow() {
        }

        public static String FullViewButtonLabel(boolean fillWindow) {
            return fillWindow ? "Exit Full View" : "Full View";
        }

        public static String FullViewMenuLabel(boolean fillWindow) {
            return fillWindow ? "Exit Full View" : "Enter Full View";
        }

        public static String LookingUpArtwork(String artworkLabel, String gameName) {
            return "Looking up " + artworkLabel.toLowerCase() + " for " + gameName;
        }

        public static String SaveStateSlotLabel(int slot, String timestampText) {
            String base = slot == 0 ? GAME_MENU_QUICK_SLOT : "Save Slot " + slot;
            return timestampText == null || timestampText.isBlank()
                    ? base
                    : base + "  [" + timestampText + "]";
        }
    }

    // ---------------------------------------------------------------------
    // Asset and preset management windows
    // ---------------------------------------------------------------------
    public static final class PaletteManager {
        // Window and heading text.
        public static final String WINDOW_TITLE = "Palette Manager";
        public static final String TITLE = "Saved Palettes";
        public static final String SUBTITLE = "Load or delete saved palettes";

        // Buttons and dialog titles.
        public static final String LOAD_BUTTON = "Load";
        public static final String IMPORT_BUTTON = "Import";
        public static final String DELETE_BUTTON = "Delete";
        public static final String DELETE_CONFIRM_TITLE = "Delete Palette";
        public static final String IMPORT_FAILED_TITLE = "Failed to import palettes";

        private PaletteManager() {
        }

        public static String WindowTitle(boolean gbcPalette) {
            return gbcPalette ? "GBC Palette Manager" : WINDOW_TITLE;
        }

        public static String Title(boolean gbcPalette) {
            return gbcPalette ? "Saved GBC Palettes" : TITLE;
        }

        public static String Subtitle(boolean gbcPalette) {
            return gbcPalette ? "Load or delete saved GBC palette sets" : SUBTITLE;
        }

        public static String DeleteConfirmTitle(boolean gbcPalette) {
            return gbcPalette ? "Delete GBC Palette" : DELETE_CONFIRM_TITLE;
        }

        public static String ImportDialogTitle(boolean gbcPalette) {
            return gbcPalette ? "Import GBC palettes" : "Import palettes";
        }

        // Confirmation messages.
        public static String DeleteConfirmMessage(String paletteName) {
            return "Delete palette \"" + paletteName + "\"?";
        }

        public static String DeleteConfirmMessage(boolean gbcPalette, String paletteName) {
            return gbcPalette
                    ? "Delete GBC palette \"" + paletteName + "\"?"
                    : DeleteConfirmMessage(paletteName);
        }

        public static String ImportSuccessMessage(boolean gbcPalette, int importedCount, int duplicateCount) {
            String paletteLabel = gbcPalette ? "GBC palette sets" : "palettes";
            return "Imported "
                    + importedCount
                    + " "
                    + paletteLabel
                    + ". Skipped "
                    + duplicateCount
                    + " duplicates.";
        }
    }

    public static final class ThemeManager {
        // Window and heading text.
        public static final String WINDOW_TITLE = "Theme Manager";
        public static final String TITLE = "Theme Library";
        public static final String SUBTITLE = "Load, delete, or restore saved themes";

        // Buttons and dialog titles.
        public static final String LOAD_BUTTON = "Load";
        public static final String DELETE_BUTTON = "Delete";
        public static final String RESTORE_DEFAULTS_BUTTON = "Restore Default Themes";
        public static final String DELETE_CONFIRM_TITLE = "Delete Theme";

        private ThemeManager() {
        }

        // Confirmation messages.
        public static String DeleteConfirmMessage(String themeName) {
            return "Delete theme \"" + themeName + "\"?";
        }
    }

    public static final class ShaderEditorWindow {
        public static final String WINDOW_TITLE = "Shader Presets";
        public static final String TITLE = "Shader Preset Editor";
        public static final String SUBTITLE = "Build custom shader chains with live controls, ordering, and preview";
        public static final String PRESET_LIST_TITLE = "Custom Presets";
        public static final String EDITOR_TITLE = "Preset Builder";
        public static final String FILE_NAME_LABEL = "Preset File";
        public static final String SHADER_ID_LABEL = "Shader ID";
        public static final String SHADER_NAME_LABEL = "Shader Name";
        public static final String DESCRIPTION_LABEL = "Description";
        public static final String RENDER_SCALE_LABEL = "Render Scale";
        public static final String STATUS_LABEL = "Status";
        public static final String EMPTY = "No custom shader presets yet";
        public static final String NEW_BUTTON = "New Preset";
        public static final String SAVE_BUTTON = "Save";
        public static final String IMPORT_BUTTON = "Import";
        public static final String DELETE_BUTTON = "Delete";
        public static final String RELOAD_BUTTON = "Reload";
        public static final String OPEN_FOLDER_BUTTON = "Open Folder";
        public static final String CLOSE_BUTTON = "Close";
        public static final String IMPORT_DIALOG_TITLE = "Import shader preset";
        public static final String DELETE_CONFIRM_TITLE = "Delete Shader Preset";
        public static final String IMPORT_FAILED_TITLE = "Failed to import shader preset";
        public static final String SAVE_FAILED_TITLE = "Failed to save shader preset";
        public static final String LOAD_FAILED_TITLE = "Failed to load shader preset";
        public static final String DELETE_FAILED_TITLE = "Failed to delete shader preset";
        public static final String OPEN_FOLDER_FAILED_TITLE = "Failed to open shader folder";
        public static final String FILE_NAME_REQUIRED = "Enter a preset file name ending in .json.";
        public static final String TEMPLATE_STATUS = "New preset template ready";
        public static final String RELOAD_STATUS = "Shader preset list reloaded";
        public static final String UNSAVED_STATUS = "Unsaved changes";
        public static final String ADD_PASS_TITLE = "Add Shader Pass";
        public static final String ADD_TO_CHAIN_BUTTON = "Add To Chain";
        public static final String CHAIN_TITLE = "Active Shader Chain";
        public static final String CHAIN_HELPER = "Passes run top to bottom. Reorder them to change the final look.";
        public static final String PASS_PARAMETERS_TITLE = "Selected Pass";
        public static final String PASS_PARAMETERS_EMPTY = "Select a pass to edit its parameters.";
        public static final String PASS_DESCRIPTION_PLACEHOLDER = "Choose a pass type to see what it does.";
        public static final String PREVIEW_TITLE = "Live Preview";
        public static final String PREVIEW_HELPER = "Preview uses the current render scale and pass order.";
        public static final String SOURCE_PREVIEW_LABEL = "Source";
        public static final String OUTPUT_PREVIEW_LABEL = "Shader Output";
        public static final String PREVIEW_UNAVAILABLE = "Preview unavailable";
        public static final String MOVE_UP_BUTTON = "Move Up";
        public static final String MOVE_DOWN_BUTTON = "Move Down";
        public static final String REMOVE_BUTTON = "Remove";
        public static final String CLEAR_CHAIN_BUTTON = "Clear Chain";

        private ShaderEditorWindow() {
        }

        public static String DeleteConfirmMessage(String presetName) {
            return "Delete shader preset \"" + presetName + "\"?";
        }

        public static String SavedStatus(String presetName) {
            return "Saved " + presetName;
        }

        public static String ImportedStatus(String presetName) {
            return "Imported " + presetName;
        }

        public static String DeletedStatus(String presetName) {
            return "Deleted " + presetName;
        }

        public static String ReloadWarningStatus(int errorCount) {
            return errorCount <= 0
                    ? RELOAD_STATUS
                    : "Reloaded presets with " + errorCount + " shader warning" + (errorCount == 1 ? "" : "s");
        }

        public static String PassChainItemLabel(int index, String passLabel) {
            return (index + 1) + ". " + passLabel;
        }

        public static String PreviewStatus(int renderScale, int passCount) {
            String passLabel = passCount == 1 ? "1 pass" : passCount + " passes";
            return "Render scale " + renderScale + "x | " + passLabel;
        }
    }

    public static final class BorderManagerWindow {
        public static final String WINDOW_TITLE = "Display Borders";
        public static final String TITLE = "Display Border Manager";
        public static final String SUBTITLE = "Import, preview, export, and manage PNG display borders";
        public static final String BORDER_LIST_TITLE = "Available Borders";
        public static final String PREVIEW_TITLE = "Live Preview";
        public static final String PREVIEW_HELPER = "Preview shows raw game output beside the active border composite.";
        public static final String SOURCE_PREVIEW_LABEL = "Source";
        public static final String OUTPUT_PREVIEW_LABEL = "Border Output";
        public static final String NAME_LABEL = "Border Name";
        public static final String SOURCE_LABEL = "Source";
        public static final String PATH_LABEL = "Location";
        public static final String CUTOUT_LABEL = "Gameplay Cutout";
        public static final String STATUS_LABEL = "Status";
        public static final String EMPTY = "No display borders are currently available";
        public static final String STATUS_OK = "Border library loaded";
        public static final String CUTOUT_NOT_AVAILABLE = "Not applicable";
        public static final String BUILT_IN_PATH = "Bundled with GameDuck";
        public static final String PREVIEW_UNAVAILABLE = "Preview unavailable";
        public static final String IMPORT_BUTTON = "Import";
        public static final String EXPORT_BUTTON = "Export";
        public static final String DELETE_BUTTON = "Delete";
        public static final String RELOAD_BUTTON = "Reload";
        public static final String REVEAL_FOLDER_BUTTON = "Reveal Folder";
        public static final String CLOSE_BUTTON = "Close";
        public static final String IMPORT_DIALOG_TITLE = "Import display border";
        public static final String EXPORT_DIALOG_TITLE = "Export display border";
        public static final String DELETE_CONFIRM_TITLE = "Delete Display Border";
        public static final String IMPORT_FAILED_TITLE = "Failed to import display border";
        public static final String EXPORT_FAILED_TITLE = "Failed to export display border";
        public static final String DELETE_FAILED_TITLE = "Failed to delete display border";
        public static final String OPEN_FOLDER_FAILED_TITLE = "Failed to reveal border folder";
        public static final String BUILT_IN_DELETE_WARNING = "Built-in borders cannot be deleted.";

        private BorderManagerWindow() {
        }

        public static String DeleteConfirmMessage(String borderName) {
            return "Delete display border \"" + borderName + "\"?";
        }

        public static String ImportedStatus(String borderName) {
            return "Imported " + borderName;
        }

        public static String ExportedStatus(String pathText) {
            return "Exported border to " + pathText;
        }

        public static String DeletedStatus(String borderName) {
            return "Deleted " + borderName;
        }

        public static String ReloadWarningStatus(int errorCount) {
            return errorCount <= 0
                    ? STATUS_OK
                    : "Reloaded borders with " + errorCount + " warning" + (errorCount == 1 ? "" : "s");
        }

        public static String PreviewStatus(int width, int height) {
            return width <= 0 || height <= 0
                    ? "Raw output preview"
                    : "Border size " + width + " x " + height;
        }
    }

    // ---------------------------------------------------------------------
    // Library browsing and ROM metadata
    // ---------------------------------------------------------------------
    public static final class LibraryWindow {
        // Main library window chrome.
        public static final String WINDOW_TITLE = "Library";
        public static final String TITLE = "Game Library";
        public static final String SUBTITLE = "Load any ROM you have played";

        // List controls: filter and view headings.
        public static final String FILTER_TITLE = "Filter";
        public static final String SORT_TITLE = "Sort";
        public static final String VIEW_TITLE = "View";

        // List controls: filter options.
        public static final String FILTER_ALL = "All Games";
        public static final String FILTER_FAVOURITES = "Favourites";
        public static final String FILTER_ROM_HACKS = "ROM Hacks";
        public static final String FILTER_BASE_ROMS = "Standard ROMs";

        // List controls: view options.
        public static final String VIEW_LIST = "List";
        public static final String VIEW_SMALL_ICONS = "Icon List";
        public static final String VIEW_LARGE_ICONS = "Large Art";
        public static final String SORT_ALPHABETICAL = "Alphabetical";
        public static final String SORT_RECENTLY_PLAYED = "Recently Played";
        public static final String SORT_NOT_PLAYED_FOR_A_WHILE = "Not Played for a While";

        // Artwork and preview placeholders.
        public static final String ART_LOADING = "Loading art...";
        public static final String ART_MISSING = "No art found";
        public static final String TITLE_SCREEN_LOADING = "Loading title screen...";
        public static final String TITLE_SCREEN_MISSING = "No title screen found";

        // Library item actions and icon tooltips.
        public static final String LOAD_BUTTON = "Load ROM";
        public static final String SAVE_MANAGER_BUTTON = "Save Manager";
        public static final String REFRESH_BUTTON = "Refresh";
        public static final String CLOSE_BUTTON = "Close";
        public static final String FAVOURITE_BUTTON = "\u2606";
        public static final String UNFAVOURITE_BUTTON = "\u2605";
        public static final String INFO_BUTTON = "\uD83D\uDEC8";
        public static final String FAVOURITE_BUTTON_TOOLTIP = "Add Favourite";
        public static final String UNFAVOURITE_BUTTON_TOOLTIP = "Remove Favourite";
        public static final String INFO_BUTTON_TOOLTIP = "Show ROM Info";

        // Library card and list metadata labels.
        public static final String VARIANT_TITLE = "Variant";
        public static final String FAVOURITE_BADGE = "Favourite";
        public static final String BASE_VARIANT = "Base ROM";

        // Empty states for the main library list.
        public static final String EMPTY = "No games have been copied into the library yet";
        public static final String EMPTY_FAVOURITES = "No favourite games yet";
        public static final String EMPTY_ROM_HACKS = "No ROM hacks are in the library yet";
        public static final String EMPTY_BASE_ROMS = "No base ROMs are in the library yet";
        public static final String EMPTY_FILTERED = "No matching games";

        // ROM info window chrome.
        public static final String INFO_WINDOW_TITLE = "ROM Info";
        public static final String INFO_WINDOW_SUBTITLE = "Cartridge details, library history, and libretro metadata for the selected ROM";

        // ROM info section titles.
        public static final String INFO_OVERVIEW_TITLE = "Overview";
        public static final String INFO_LIBRETRO_TITLE = "Libretro Metadata";
        public static final String INFO_STORAGE_TITLE = "File info";

        // ROM info field labels.
        public static final String INFO_PATH_TITLE = "ROM Path";
        public static final String INFO_LAST_PLAYED_TITLE = "Last Played";
        public static final String INFO_TARGET_HARDWARE_TITLE = "Target Hardware";
        public static final String INFO_COMPATIBILITY_TITLE = "Compatibility";
        public static final String INFO_HEADER_TITLE = "Header Title";
        public static final String INFO_MAPPER_TITLE = "Mapper";
        public static final String INFO_ROM_SIZE_TITLE = "ROM Size";
        public static final String INFO_SAVE_SUPPORT_TITLE = "Battery Save";
        public static final String INFO_PUBLISHER_TITLE = "Publisher";
        public static final String INFO_RELEASE_YEAR_TITLE = "Release Year";
        public static final String INFO_DATABASE_TITLE = "Libretro Database";

        // ROM info value placeholders.
        public static final String INFO_NO_SELECTION = "No library game selected";
        public static final String INFO_UNKNOWN_VALUE = "Unknown";
        public static final String INFO_LOADING_VALUE = "Loading...";

        // ROM info actions and error titles.
        public static final String INFO_OPEN_EXPLORER_BUTTON = "Open in Explorer";
        public static final String INFO_GET_ARTWORK_BUTTON = "Get Artwork";
        public static final String INFO_DELETE_ROM_BUTTON = "Delete ROM";
        public static final String INFO_LOAD_ERROR_TITLE = "Failed to open ROM info";
        public static final String INFO_EXPLORER_ERROR_TITLE = "Failed to open Explorer";
        public static final String INFO_ARTWORK_FETCH_FAILED_TITLE = "Failed to get artwork";
        public static final String INFO_DELETE_CONFIRM_TITLE = "Delete ROM";
        public static final String INFO_DELETE_FAILED_TITLE = "Failed to delete ROM";

        // ROM info units and formatting fragments.
        public static final String INFO_ROM_BANKS_SUFFIX = "banks";
        public static final String INFO_BYTE_UNIT_B = "B";
        public static final String INFO_BYTE_UNIT_KB = "KB";
        public static final String INFO_BYTE_UNIT_MB = "MB";

        private LibraryWindow() {
        }

        // Dynamic labels for library list cards and info panes.
        public static String VariantLabel(java.util.List<String> patchNames) {
            return patchNames == null || patchNames.isEmpty()
                    ? BASE_VARIANT
                    : "Hack: " + String.join(" + ", patchNames);
        }

        public static String InfoTargetHardware(boolean cgbOnly, boolean cgbEnhanced) {
            if (cgbOnly) {
                return "Game Boy Color";
            }
            if (cgbEnhanced) {
                return "Dual-mode cartridge";
            }
            return "Game Boy";
        }

        public static String InfoCompatibility(boolean cgbOnly, boolean cgbEnhanced) {
            if (cgbOnly) {
                return "GBC only";
            }
            if (cgbEnhanced) {
                return "GB + GBC Enhancements";
            }
            return "Standard GB";
        }

        public static String InfoBatterySave(boolean hasBatterySave) {
            return hasBatterySave ? "Supported" : "Not supported";
        }

        public static String InfoRomSizeSummary(String formattedByteSize, int bankCount) {
            return formattedByteSize + " | " + bankCount + " " + INFO_ROM_BANKS_SUFFIX;
        }

        public static String InfoByteSize(long bytes) {
            if (bytes < 1024L) {
                return bytes + " " + INFO_BYTE_UNIT_B;
            }
            if (bytes < 1024L * 1024L) {
                return String.format("%.1f %s", bytes / 1024.0, INFO_BYTE_UNIT_KB);
            }
            return String.format("%.1f %s", bytes / (1024.0 * 1024.0), INFO_BYTE_UNIT_MB);
        }
    }

    // ---------------------------------------------------------------------
    // Global dialogs, actions, and supporting metadata
    // ---------------------------------------------------------------------
    public static final class GuiActions {
        // File dialog titles.
        public static final String LOAD_ROM_DIALOG_TITLE = "Select a ROM or IPS patch";
        public static final String LOAD_IPS_DIALOG_TITLE = "Select an IPS patch";
        public static final String BASE_ROM_DIALOG_TITLE = "Select the base ROM for this IPS patch";

        // Error dialog titles.
        public static final String LOAD_ROM_ERROR_TITLE = "Failed to load ROM";
        public static final String LOAD_IPS_ERROR_TITLE = "Failed to load IPS patch";
        public static final String LIBRARY_LOAD_ERROR_TITLE = "Failed to load library game";
        public static final String QUICK_SAVE_ERROR_TITLE = "Failed to quick save";
        public static final String QUICK_LOAD_ERROR_TITLE = "Failed to quick load";
        public static final String SAVE_STATE_ERROR_TITLE = "Failed to save state";
        public static final String LOAD_STATE_ERROR_TITLE = "Failed to load state";

        // Confirmation dialog titles and messages.
        public static final String EXIT_CONFIRM_TITLE = "Exit";
        public static final String EXIT_CONFIRM_MESSAGE = "Are you sure you want to exit?";

        // Short status messages shown after save/load actions.
        public static final String QUICK_SAVE_STATUS = "Quick saved";
        public static final String QUICK_LOAD_STATUS = "Quick loaded";

        private GuiActions() {
        }

        // Dynamic status labels used by quick slot and numbered slot flows.
        public static String SaveStateStatus(int slot) {
            return slot == 0 ? QUICK_SAVE_STATUS : "Saved slot " + slot;
        }

        public static String LoadStateStatus(int slot) {
            return slot == 0 ? QUICK_LOAD_STATUS : "Loaded slot " + slot;
        }
    }

    public static final class GameArt {
        // Artwork source labels exposed in the UI.
        public static final String SOURCE_LIBRETRO = "Libretro thumbnails";
        public static final String SOURCE_LIBRETRO_BOXART = "Libretro box art";
        public static final String SOURCE_LIBRETRO_TITLE = "Libretro title screen";
        public static final String SOURCE_LIBRETRO_SCREENSHOT = "Libretro screenshot";
        public static final String SOURCE_NONE = "Artwork hidden";

        // Mode picker labels.
        public static final String BOX_ART_LABEL = "Box Art";
        public static final String TITLE_SCREEN_LABEL = "Title Screen";
        public static final String SCREENSHOT_LABEL = "Screenshot";
        public static final String NONE_LABEL = "None";

        // Mode picker descriptions.
        public static final String BOX_ART_DESCRIPTION = "Show libretro box art";
        public static final String TITLE_SCREEN_DESCRIPTION = "Show libretro title-screen captures";
        public static final String SCREENSHOT_DESCRIPTION = "Show libretro gameplay screenshots";
        public static final String NONE_DESCRIPTION = "Hide the game art panel in the main window";

        private GameArt() {
        }
    }

    public static final class CheatManagerWindow {
        public static final String WINDOW_TITLE = "Cheat Manager";
        public static final String TITLE = "Cheats";
        public static final String SUBTITLE = "Add per-game memory cheats. Address, compare, and value fields use hexadecimal.";
        public static final String LIST_TITLE = "Current Cheats";
        public static final String LIST_HELPER = "Cheats are saved for the loaded game and re-applied automatically next time.";
        public static final String EDITOR_TITLE = "Cheat Editor";
        public static final String EDITOR_HELPER = "ROM addresses act as read overrides. RAM and register addresses are forced live while enabled.";
        public static final String LOADED_GAME_TITLE = "Loaded Game";
        public static final String NAME_LABEL = "Name";
        public static final String ADDRESS_LABEL = "Address";
        public static final String VALUE_LABEL = "Value";
        public static final String COMPARE_LABEL = "Compare";
        public static final String COMPARE_HINT = "Optional. Leave blank to always apply.";
        public static final String ENABLED_LABEL = "Enabled";
        public static final String ADDRESS_HELPER = "Hex 0000 to FFFF";
        public static final String VALUE_HELPER = "Hex 00 to FF";
        public static final String NEW_BUTTON = "New Cheat";
        public static final String SAVE_BUTTON = "Save Cheat";
        public static final String DELETE_BUTTON = "Delete Cheat";
        public static final String EMPTY_EDITOR = "Select a cheat or create a new one";
        public static final String STATUS_READY = "Ready";
        public static final String STATUS_NEW = "Enter a cheat and save to apply it immediately.";
        public static final String STATUS_SYNCING_LIBRETRO = "Looking up libretro cheats...";
        public static final String STATUS_SAVED = "Cheat saved";
        public static final String STATUS_DELETED = "Cheat deleted";
        public static final String DELETE_CONFIRM_TITLE = "Delete Cheat";
        public static final String NO_GAME_LOADED = "Load a game before opening the cheat manager.";
        public static final String VALIDATION_ERROR_TITLE = "Invalid Cheat";

        private CheatManagerWindow() {
        }

        public static String CheatCountBadge(int cheatCount) {
            return cheatCount == 1 ? "1 Cheat" : cheatCount + " Cheats";
        }

        public static String DeleteConfirmMessage(String cheatName) {
            return "Delete cheat \"" + cheatName + "\"?";
        }

        public static String LibretroImportedMessage(int cheatCount, String gameName) {
            String sourceName = gameName == null || gameName.isBlank() ? "libretro" : gameName;
            return (cheatCount == 1 ? "Imported 1 libretro cheat from "
                    : "Imported " + cheatCount + " libretro cheats from ")
                    + sourceName;
        }

        public static String MissingHexMessage(String fieldName, String expectedFormat) {
            return fieldName + " is required.\nExpected format: " + expectedFormat;
        }

        public static String InvalidHexMessage(String fieldName, String expectedFormat) {
            return fieldName + " must be hexadecimal.\nExpected format: " + expectedFormat;
        }

        public static String DefaultCheatName(int address, int value) {
            return String.format("Cheat %04X=%02X", address & 0xFFFF, value & 0xFF);
        }
    }

    // ---------------------------------------------------------------------
    // Options reference text and preset catalogs
    // ---------------------------------------------------------------------
    public static final class AppShortcuts {
        // Shortcut labels.
        public static final String OPTIONS_LABEL = "Options";
        public static final String EXIT_LABEL = "Exit";
        public static final String OPEN_GAME_LABEL = "Open Game";
        public static final String PAUSE_GAME_LABEL = "Pause Game";
        public static final String CLOSE_GAME_LABEL = "Close Game";
        public static final String SAVE_STATE_LABEL = "Quick Save";
        public static final String LOAD_STATE_LABEL = "Quick Load";
        public static final String TOGGLE_FULL_VIEW_LABEL = "Toggle Full View";
        public static final String TOGGLE_FULLSCREEN_LABEL = "Toggle Fullscreen";
        public static final String TOGGLE_MAXIMISE_LABEL = "Toggle Maximise";

        // Shortcut descriptions.
        public static final String OPTIONS_DESCRIPTION = "Open the options window";
        public static final String EXIT_DESCRIPTION = "Close the app";
        public static final String OPEN_GAME_DESCRIPTION = "Load a ROM";
        public static final String PAUSE_GAME_DESCRIPTION = "Pause or resume the current game";
        public static final String CLOSE_GAME_DESCRIPTION = "Stop the current ROM";
        public static final String SAVE_STATE_DESCRIPTION = "Save the current emulator quick state";
        public static final String LOAD_STATE_DESCRIPTION = "Load the current emulator quick state";
        public static final String TOGGLE_FULL_VIEW_DESCRIPTION = "Toggle full view mode";
        public static final String TOGGLE_FULLSCREEN_DESCRIPTION = "Toggle fullscreen mode";
        public static final String TOGGLE_MAXIMISE_DESCRIPTION = "Toggle the maximised window state";

        private AppShortcuts() {
        }
    }

    public static final class GameNameDisplayMode {
        // Display mode labels.
        public static final String NONE_LABEL = "Show all bracketed content";
        public static final String ROUND_LABEL = "Hide (...) only";
        public static final String SQUARE_LABEL = "Hide [...] only";
        public static final String BOTH_LABEL = "Hide (...) and [...]";

        // Display mode descriptions.
        public static final String NONE_DESCRIPTION = "Keep both (...) and [...] segments in displayed names";
        public static final String ROUND_DESCRIPTION = "Remove round-bracketed segments from displayed names";
        public static final String SQUARE_DESCRIPTION = "Remove square-bracketed segments from displayed names";
        public static final String BOTH_DESCRIPTION = "Remove round and square bracketed segments from displayed names";

        private GameNameDisplayMode() {
        }
    }

    public static final class ThemePresets {
        // Built-in preset labels.
        public static final String HARBOR_LABEL = "Harbour";
        public static final String MINT_DMG_LABEL = "Mint DMG";
        public static final String GRAPHITE_LABEL = "Graphite";
        public static final String MIDNIGHT_LABEL = "Midnight";
        public static final String SUNSET_LABEL = "Sunset";

        // Built-in preset descriptions.
        public static final String HARBOR_DESCRIPTION = "Cool blue default with crisp contrast";
        public static final String MINT_DMG_DESCRIPTION = "A soft green inspired by the DMG screen";
        public static final String GRAPHITE_DESCRIPTION = "More neutral default than harbour";
        public static final String MIDNIGHT_DESCRIPTION = "Deep slate surfaces with crisp blue highlights";
        public static final String SUNSET_DESCRIPTION = "Warm copper accents and soft paper";

        private ThemePresets() {
        }
    }

    public static final class ThemeColorRoles {
        // Theme role labels.
        public static final String APP_BACKGROUND_LABEL = "App Background";
        public static final String SURFACE_LABEL = "Surface";
        public static final String ACCENT_LABEL = "Accent";
        public static final String MUTED_TEXT_LABEL = "Muted Text";
        public static final String DISPLAY_FRAME_LABEL = "Display Frame";
        public static final String SECTION_HIGHLIGHT_LABEL = "Section Tint";

        // Theme role descriptions.
        public static final String APP_BACKGROUND_DESCRIPTION = "Window backgrounds";
        public static final String SURFACE_DESCRIPTION = "Cards, panels, and menu surfaces";
        public static final String ACCENT_DESCRIPTION = "Primary buttons and headings";
        public static final String MUTED_TEXT_DESCRIPTION = "Secondary labels and helper text";
        public static final String DISPLAY_FRAME_DESCRIPTION = "The the emulator display bezel";
        public static final String SECTION_HIGHLIGHT_DESCRIPTION = "Highlighted panels and selection tint";

        private ThemeColorRoles() {
        }
    }

    public static final class AudioEnhancements {
        // Preset labels.
        public static final String SOFT_LOW_PASS_LABEL = "Soft Low-Pass";
        public static final String POCKET_SPEAKER_LABEL = "Pocket Speaker";
        public static final String SOFT_CLIP_LABEL = "Soft Clip";
        public static final String STEREO_WIDEN_LABEL = "Stereo Widen";
        public static final String ROOM_REVERB_LABEL = "Room Reverb";
        public static final String SHIMMER_CHORUS_LABEL = "Shimmer Chorus";
        public static final String DUB_ECHO_LABEL = "Dub Echo";
        public static final String UNDERWATER_LABEL = "Underwater";

        // Preset descriptions.
        public static final String SOFT_LOW_PASS_DESCRIPTION = "Reduces harsh highs for a warmer, easier tone on the ears";
        public static final String POCKET_SPEAKER_DESCRIPTION = "Band-limits lows and highs to feel closer to the Game Boy's speaker";
        public static final String SOFT_CLIP_DESCRIPTION = "Clips and increases volume to add density and weight";
        public static final String STEREO_WIDEN_DESCRIPTION = "Expands the left and right image slightly after the mix";
        public static final String ROOM_REVERB_DESCRIPTION = "Adds a short reverb to create extra depth";
        public static final String SHIMMER_CHORUS_DESCRIPTION = "Adds washiness to the audio, for creativity";
        public static final String DUB_ECHO_DESCRIPTION = "Adds a left/right ping pong echo";
        public static final String UNDERWATER_DESCRIPTION = "Hello. I am under the water.";

        private AudioEnhancements() {
        }
    }

    // ---------------------------------------------------------------------
    // Options window text catalog
    // ---------------------------------------------------------------------
    public static final class OptionsWindow {
        // Window chrome and tab labels.
        public static final String WINDOW_TITLE = "Options";
        public static final String HEADER_TITLE = "Options";
        public static final String HEADER_SUBTITLE = "Adjust the display, controls, audio, and emulation behaviour";
        public static final String TAB_PALETTE = "Palette";
        public static final String TAB_CONTROLS = "Controls";
        public static final String TAB_SOUND = "Sound";
        public static final String TAB_EMULATION = "Emulation";
        public static final String TAB_WINDOW = "Window";
        public static final String TAB_LIBRARY = "Library";
        public static final String TAB_THEME = "Theme";

        // -----------------------------------------------------------------
        // Section card titles and descriptions used across the tab pages.
        // Keep each title/description pair together for the card it belongs to.
        // -----------------------------------------------------------------
        public static final String SECTION_PALETTE_TITLE = "Game Boy Colourisation";
        public static final String SECTION_PALETTE_DESCRIPTION = "";
        public static final String SECTION_GBC_TITLE = "Game Boy Color Colourisation";
        public static final String SECTION_GBC_DESCRIPTION = "";
        public static final String SECTION_CONTROLS_TITLE = "Input Mapping";
        public static final String SECTION_CONTROLS_DESCRIPTION = "Open the dedicated visual mapper for keyboard and controller rebinding";
        public static final String SECTION_SHORTCUTS_TITLE = "App Shortcuts";
        public static final String SECTION_SHORTCUTS_DESCRIPTION = "Customise the keyboard shortcuts";
        public static final String SECTION_SOUND_TITLE = "Audio Output";
        public static final String SECTION_SOUND_DESCRIPTION = "Adjust various audio settings and effects";
        public static final String SECTION_EMULATION_TITLE = "Save Data and Boot ROM";
        public static final String SECTION_EMULATION_DESCRIPTION = "";
        public static final String SECTION_WINDOW_TITLE = "Main Window Layout";
        public static final String SECTION_WINDOW_DESCRIPTION = "";
        public static final String SECTION_DISPLAY_SHADER_TITLE = "Display Shaders";
        public static final String SECTION_DISPLAY_SHADER_DESCRIPTION = "Apply built-in looks or load your own JSON presets and plugin shaders";
        public static final String SECTION_LIBRARY_TITLE = "Library Preferences";
        public static final String SECTION_LIBRARY_DESCRIPTION = "Control displayed game names and how much recent-play history appears in the Game menu";
        public static final String SECTION_THEME_LIBRARY_TITLE = "Theme Library";
        public static final String SECTION_THEME_LIBRARY_DESCRIPTION = "Save the active theme, browse saved JSON themes, or restore the bundled defaults";

        // -----------------------------------------------------------------
        // Palette tab: DMG palette editor.
        // -----------------------------------------------------------------
        public static final String ACTIVE_DMG_PALETTE_TITLE = "Active Game Boy Palette";
        public static final String ACTIVE_DMG_PALETTE_HELPER = "Click any swatch to edit that tone";
        public static final String SAVE_CURRENT_PALETTE = "Save Current Palette";
        public static final String SAVE_CURRENT_PALETTE_HELPER = "";
        public static final String SAVE_PALETTE_BUTTON = "Save Palette";
        public static final String BROWSE_BUTTON = "Browse";
        public static final String RESET_PALETTE_BUTTON = "Default Palette";
        public static final String[] DMG_TONE_NAMES = { "Background", "Light", "Medium", "Dark" };

        // -----------------------------------------------------------------
        // Palette tab: GBC colourisation controls.
        // -----------------------------------------------------------------
        public static final String GBC_COMPATIBLE_MODE_TITLE = "GBC Colourisation mode";
        public static final String GBC_COMPATIBLE_MODE_HELPER = "Switch between GBC and GB emulation for games compatible with both (Game restart required)";
        public static final String GBC_NON_CGB_MODE_TITLE = "Non-GBC Colour Mode";
        public static final String GBC_NON_CGB_MODE_HELPER = "Choose between using the GB or GBC colourisation of GB roms";
        public static final String DMG_PALETTE_MODE_GB = "GB Original";
        public static final String DMG_PALETTE_MODE_GBC = "GBC Colourisation";
        public static final String GBC_COMPATIBLE_MODE_FULL_COLOUR = "GBC Full colour";
        public static final String GBC_COMPATIBLE_MODE_GB_PALETTE = "GB Colourisation";
        public static final String GBC_BACKGROUND_PALETTE_TITLE = "Background Palette";
        public static final String GBC_BACKGROUND_PALETTE_HELPER = "Used for tiles and window pixels";
        public static final String GBC_SPRITE0_PALETTE_TITLE = "Sprite Palette 0";
        public static final String GBC_SPRITE0_PALETTE_HELPER = "Used by sprites selecting OBP0";
        public static final String GBC_SPRITE1_PALETTE_TITLE = "Sprite Palette 1";
        public static final String GBC_SPRITE1_PALETTE_HELPER = "Used by sprites selecting OBP1";
        public static final String SAVE_CURRENT_GBC_PALETTE = "Save Current GBC Palette";
        public static final String SAVE_CURRENT_GBC_PALETTE_HELPER = "";
        public static final String RESET_GBC_SETTINGS_BUTTON = "Reset GBC Settings";

        // -----------------------------------------------------------------
        // Theme tab: saved theme handling and active theme editing.
        // -----------------------------------------------------------------
        public static final String ACTIVE_APP_THEME_TITLE = "Active App Theme";
        public static final String ACTIVE_APP_THEME_HELPER = "Click any swatch to customise the current window chrome and control colours";
        public static final String SAVE_CURRENT_THEME = "Save Current Theme";
        public static final String SAVE_CURRENT_THEME_HELPER = "Save the active window theme or reopen a saved JSON theme";
        public static final String SAVE_THEME_BUTTON = "Save Theme";
        public static final String RESET_THEME_BUTTON = "Reset Theme";

        // -----------------------------------------------------------------
        // Controls tab: player inputs and app shortcut rebinding.
        // -----------------------------------------------------------------
        public static final String PLAYER_CONTROLS_TITLE = "Player Controls";
        public static final String PLAYER_CONTROLS_DESCRIPTION = "Keyboard and controller rebinding now live in the visual mapper window.";
        public static final String WINDOW_SHORTCUTS_TITLE = "Window Shortcuts";
        public static final String WINDOW_SHORTCUTS_DESCRIPTION = "Rebind app functions to keyboard shortcuts and controller inputs.";
        public static final String PLAYER_CONTROLS_BADGE = "Game Boy Input";
        public static final String WINDOW_SHORTCUTS_BADGE = "App Action";
        public static final String DMG_BADGE = "Game Boy";
        public static final String APP_BADGE = "APP";
        public static final String SHORTCUT_KEYBOARD_LABEL = "Keyboard";
        public static final String SHORTCUT_CONTROLLER_LABEL = "Controller";
        public static final String INPUT_MAPPER_WINDOW_TITLE = "Input Mapper";
        public static final String INPUT_MAPPER_WINDOW_HEADER = "Input Mapper";
        public static final String INPUT_MAPPER_WINDOW_SUBTITLE = "Switch between keyboard and controller tabs, then click the callouts around the Game Boy layout to rebind each button.";
        public static final String INPUT_MAPPER_KEYBOARD_TAB = "Keyboard";
        public static final String INPUT_MAPPER_CONTROLLER_TAB = "Controller";
        public static final String INPUT_MAPPER_LAUNCH_TITLE = "Visual Input Mapper";
        public static final String INPUT_MAPPER_LAUNCH_DESCRIPTION = "Open a dedicated rebinding window with keyboard and controller tabs, a central Game Boy layout, and click targets at the end of each callout arrow.";
        public static final String INPUT_MAPPER_OPEN_BUTTON = "Open Input Mapper";
        public static final String INPUT_MAPPER_CANVAS_HINT = "Hover a callout to preview the matching control. Capture dialogs still support Escape to cancel.";
        public static final String KEYBOARD_MAPPER_TITLE = "Keyboard Mapping";
        public static final String KEYBOARD_MAPPER_DESCRIPTION = "Click any label around the Game Boy to assign a new keyboard key. Duplicate assignments are swapped automatically.";
        public static final String CONTROLLER_MAPPER_TITLE = "Controller Mapping";
        public static final String CONTROLLER_MAPPER_DESCRIPTION = "Click any label around the Game Boy to bind a raw controller input, or use Rebind All to walk every button in order.";
        public static final String RESET_CONTROLS_BUTTON = "Reset Controls";
        public static final String REBIND_ALL_CONTROLS_BUTTON = "Rebind All";
        public static final String RESET_SHORTCUTS_BUTTON = "Reset App Shortcuts";
        public static final String CONTROLLER_WINDOW_TITLE = "Controller Options";
        public static final String CONTROLLER_WINDOW_HEADER = "Controller Input";
        public static final String CONTROLLER_WINDOW_SUBTITLE = "Choose the active gamepad, adjust deadzone behaviour, and rebind controller inputs for every emulated Game Boy button.";
        public static final String SECTION_CONTROLLER_TITLE = "Controller Input";
        public static final String SECTION_CONTROLLER_DESCRIPTION = "Choose a detected controller, verify live input, and then use the visual mapper window for controller rebinding.";
        public static final String CONTROLLER_ENABLE_CHECKBOX = "Enable Controller Input";
        public static final String CONTROLLER_SELECTION_LABEL = "Preferred Controller";
        public static final String CONTROLLER_ACTIVE_LABEL = "Active Controller";
        public static final String CONTROLLER_STATUS_LABEL = "Connection Status";
        public static final String CONTROLLER_DEADZONE_LABEL = "Stick Deadzone";
        public static final String CONTROLLER_REFRESH_BUTTON = "Refresh Controllers";
        public static final String CONTROLLER_RESET_BUTTON = "Reset Controller Bindings";
        public static final String CONTROLLER_REBIND_ALL_BUTTON = "Rebind All";
        public static final String CONTROLLER_BINDINGS_TITLE = "Controller Bindings";
        public static final String CONTROLLER_AUTO_SELECT = "Auto-select first connected controller";
        public static final String CONTROLLER_NONE_CONNECTED = "No controller connected";
        public static final String CONTROLLER_STATUS_CONNECTED = "Connected";
        public static final String CONTROLLER_STATUS_DISABLED = "Disabled";
        public static final String CONTROLLER_STATUS_DISCONNECTED = "Disconnected";
        public static final String CONTROLLER_STATUS_UNAVAILABLE = "Unavailable";
        public static final String CONTROLLER_STATUS_HELPER = "Generic gamepads, joysticks, and D-pads are supported.";
        public static final String CONTROLLER_CAPTURE_HELPER = "Press any controller button, D-pad direction, or stick direction. Press Escape to cancel.";
        public static final String CONTROLLER_NO_ACTIVE_DEVICE_MESSAGE = "Connect a controller or pick a detected device first.";
        public static final String CONTROLLER_LIVE_INPUTS_LABEL = "Live Raw Inputs";
        public static final String CONTROLLER_MAPPED_BUTTONS_LABEL = "Mapped Game Boy Buttons";
        public static final String CONTROLLER_LIVE_NONE = "No active inputs";
        public static final String CONTROLLER_MAPPED_NONE = "No mapped buttons pressed";
        public static final String CONTROLLER_MAPPED_DISABLED = "Controller input is disabled";
        public static final String CONTROLLER_BINDINGS_HELPER = "Duplicate assignments are swapped automatically.";

        // -----------------------------------------------------------------
        // Sound tab: playback and channel mixer.
        // -----------------------------------------------------------------
        public static final String PLAYBACK_TITLE = "Playback";
        public static final String PLAYBACK_HELPER = "Enable or Disable audio from the APU";
        public static final String MASTER_VOLUME_TITLE = "Master Volume";
        public static final String MASTER_VOLUME_HELPER = "Applies to all four Game Boy audio channels";
        public static final String CHANNEL_MIXER_TITLE = "Channel Mixer";
        public static final String CHANNEL_MIXER_HELPER = "Mute and trim each Game Boy voice";
        public static final String SOUND_ENABLED_CHECKBOX = "Enable Sound";
        public static final String MUTE_CHECKBOX = "Mute";
        public static final String QUIET_LABEL = "Quiet";
        public static final String LOUD_LABEL = "Loud";
        public static final String RESET_SOUND_BUTTON = "Reset Sound";

        // -----------------------------------------------------------------
        // Sound tab: enhancement chain builder.
        // -----------------------------------------------------------------
        public static final String AUDIO_ENHANCEMENTS_TITLE = "Audio Enhancements";
        public static final String AUDIO_ENHANCEMENTS_HELPER = "Apply stackable audio enhancements. Drag cards to reorder the chain and turn the knobs to shape each effect";
        public static final String AUDIO_ENHANCEMENTS_ENABLED_CHECKBOX = "Enable Effects Chain";
        public static final String ADD_PRESET_TITLE = "Add Effect";
        public static final String ACTIVE_CHAIN_TITLE = "Active Chain";
        public static final String ACTIVE_CHAIN_HELPER = "Drag by the handle, remove with -, and use the knobs for blend and tone";
        public static final String EFFECT_INTENSITY_TITLE = "Blend";
        public static final String EFFECT_INTENSITY_HELPER = "Blend the selected effect from subtle to full strength";
        public static final String EFFECT_INTENSITY_NONE = "Add an effect to adjust its blend";
        public static final String PRESET_DESCRIPTION_PLACEHOLDER = "Effect description will show here";
        public static final String ADD_TO_CHAIN_BUTTON = "Add to Chain";
        public static final String MOVE_UP_BUTTON = "Move Up";
        public static final String MOVE_DOWN_BUTTON = "Move Down";
        public static final String REMOVE_BUTTON = "Remove";
        public static final String CLEAR_CHAIN_BUTTON = "Clear Chain";
        public static final String AUDIO_ENHANCEMENTS_EMPTY_STATE = "Add an effect to start building a chain";

        // -----------------------------------------------------------------
        // Window tab: main window layout and library naming controls.
        // -----------------------------------------------------------------
        public static final String WINDOW_FILL_CHECKBOX = "Fill window with output";
        public static final String WINDOW_INTEGER_SCALE_CHECKBOX = "Use integer scaling";
        public static final String DISPLAY_FRAME_BLENDING_CHECKBOX = "Enable frame blending";
        public static final String SHOW_DISPLAY_FPS_CHECKBOX = "Show FPS counter";
        public static final String SERIAL_OUTPUT_CHECKBOX = "Show serial output";
        public static final String GAME_ART_MODE_LABEL = "Game Art";
        public static final String RESET_WINDOW_BUTTON = "Reset Window Layout";
        public static final String DISPLAY_BORDER_LABEL = "Display Border";
        public static final String BORDER_PREVIEW_LABEL = "Preview";
        public static final String BORDER_SOURCE_LABEL = "Source";
        public static final String BORDER_PATH_LABEL = "Location";
        public static final String BORDER_CUTOUT_LABEL = "Gameplay Cutout";
        public static final String BORDER_STATUS_LABEL = "Library Status";
        public static final String BORDER_PATH_BUILT_IN = "Bundled with GameDuck";
        public static final String BORDER_STATUS_OK_HELPER = "No border load errors were reported the last time the folder was scanned.";
        public static final String OPEN_BORDER_MANAGER_BUTTON = "Border Manager";
        public static final String DISPLAY_SHADER_LABEL = "Display Shader";
        public static final String SHADER_DESCRIPTION_LABEL = "Description";
        public static final String SHADER_SOURCE_LABEL = "Source";
        public static final String SHADER_PATH_LABEL = "Location";
        public static final String SHADER_STATUS_LABEL = "Library Status";
        public static final String SHADER_DESCRIPTION_FALLBACK = "No shader description was provided.";
        public static final String SHADER_PATH_BUILT_IN = "Bundled with GameDuck";
        public static final String SHADER_STATUS_OK_HELPER = "No shader load errors were reported the last time the folder was scanned.";
        public static final String RELOAD_SHADERS_BUTTON = "Reload Shader Folder";
        public static final String OPEN_SHADER_FOLDER_BUTTON = "Open Shader Folder";
        public static final String OPEN_SHADER_EDITOR_BUTTON = "Shader Editor";
        public static final String RESET_SHADER_BUTTON = "Reset Shader";
        public static final String LIBRARY_MODE_LABEL = "Bracketed Content";
        public static final String RECENT_MENU_LIMIT_LABEL = "Load Recent Size";
        public static final String RECENT_MENU_LIMIT_HELPER = "How many games appear in Game > Load Recent";
        public static final String RESET_LIBRARY_BUTTON = "Reset Library Settings";

        // -----------------------------------------------------------------
        // Emulation tab: save-state manager launcher and window copy.
        // -----------------------------------------------------------------
        public static final String SAVE_STATE_MANAGER_OPEN_BUTTON = "Save State Manager";
        public static final String SAVE_STATE_MANAGER_WINDOW_TITLE = "Save State Manager";
        public static final String SAVE_STATE_MANAGER_SUBTITLE = "Manage save states for tracked games";
        public static final String SAVE_STATE_MANAGER_EMPTY_TITLE = "No tracked library games yet";
        public static final String SAVE_STATE_MANAGER_EMPTY_HELPER = "Ran games appear here";
        public static final String SAVE_STATE_MANAGER_FILTERED_EMPTY_TITLE = "No matching tracked ROMs";
        public static final String SAVE_STATE_MANAGER_FILTERED_EMPTY_HELPER = "Try a different search or console filter";
        public static final String SAVE_STATE_MANAGER_SLOT_TITLE = "Save State Slots";
        public static final String SAVE_STATE_MANAGER_SLOT_HELPER = "Quick Slot plus slots 1-9 for the selected ROM";
        public static final String SAVE_STATE_MANAGER_PATH_TITLE = "Selected Slot Path";
        public static final String SAVE_STATE_MANAGER_ACTIONS_TITLE = "State Actions";
        public static final String SAVE_STATE_MANAGER_ACTIONS_HELPER = "";
        public static final String SAVE_STATE_MANAGER_MOVE_TITLE = "Move State";
        public static final String SAVE_STATE_MANAGER_MOVE_HELPER = "Move the selected state into another slot for this ROM (THIS DELETES THE TARGET STATE)";
        public static final String SAVE_STATE_MANAGER_IMPORT_BUTTON = "Import";
        public static final String SAVE_STATE_MANAGER_EXPORT_BUTTON = "Export";
        public static final String SAVE_STATE_MANAGER_DELETE_BUTTON = "Delete Slot";
        public static final String SAVE_STATE_MANAGER_DELETE_ALL_BUTTON = "Delete All";
        public static final String SAVE_STATE_MANAGER_MOVE_BUTTON = "Move";
        public static final String SAVE_STATE_MANAGER_SLOT_EMPTY = "Empty";
        public static final String SAVE_STATE_MANAGER_SLOT_PRESENT = "Saved";
        public static final String SAVE_STATE_MANAGER_CURRENT_GAME_BADGE = "Loaded Now";
        public static final String SAVE_STATE_MANAGER_NO_SLOT_BADGE = "No State";
        public static final String SAVE_STATE_MANAGER_READY_BADGE = "State Ready";
        public static final String SAVE_STATE_MANAGER_IMPORT_DIALOG_TITLE = "Import save state";
        public static final String SAVE_STATE_MANAGER_EXPORT_DIALOG_TITLE = "Export save state";
        public static final String SAVE_STATE_MANAGER_IMPORT_FAILED_TITLE = "Failed to import save state";
        public static final String SAVE_STATE_MANAGER_EXPORT_FAILED_TITLE = "Failed to export save state";
        public static final String SAVE_STATE_MANAGER_DELETE_FAILED_TITLE = "Failed to delete save state";
        public static final String SAVE_STATE_MANAGER_MOVE_FAILED_TITLE = "Failed to move save state";
        public static final String SAVE_STATE_MANAGER_DELETE_CONFIRM_TITLE = "Delete Save State";
        public static final String SAVE_STATE_MANAGER_DELETE_ALL_CONFIRM_TITLE = "Delete All Save States";
        public static final String SAVE_STATE_MANAGER_TARGET_SLOT_LABEL = "Target Slot";
        public static final String SAVE_STATE_MANAGER_SLOT_PATH_MISSING = "No managed save-state file exists for this slot";
        public static final String SAVE_STATE_MANAGER_SLOT_SUMMARY_TITLE = "Saved Slots";

        // -----------------------------------------------------------------
        // Emulation tab: save-data manager launcher and detail copy.
        // -----------------------------------------------------------------
        public static final String SAVE_DATA_TITLE = "Save Data Manager";
        public static final String SAVE_DATA_DESCRIPTION = "Manage save data for all compatible games";
        public static final String SAVE_MANAGER_WINDOW_TITLE = "Save Data Manager";
        public static final String SAVE_MANAGER_SUBTITLE = "";
        public static final String SAVE_MANAGER_EMPTY_TITLE = "No tracked save games yet";
        public static final String SAVE_MANAGER_EMPTY_HELPER = "Load a game with saving functionality to track it here";
        public static final String SAVE_MANAGER_FILTERED_EMPTY_TITLE = "No matching tracked games";
        public static final String SAVE_MANAGER_FILTERED_EMPTY_HELPER = "Try a different search or console filter";
        public static final String SAVE_MANAGER_CURRENT_GAME_BADGE = "Loaded Now";
        public static final String SAVE_MANAGER_REFRESH_BUTTON = "Refresh";
        public static final String SAVE_MANAGER_SIZE_SUMMARY_TITLE = "Save Sizes";
        public static final String SAVE_MANAGER_ACTIONS_TITLE = "Save Actions";
        public static final String SAVE_MANAGER_ACTIONS_HELPER = "Import, export, or delete save data";
        public static final String SAVE_MANAGER_LAUNCH_TITLE = "Tracked game count";
        public static final String SAVE_MANAGER_LAUNCH_HELPER = "";
        public static final String SAVE_MANAGER_OPEN_BUTTON = "Open Save Manager";
        public static final String SAVE_DATA_MANAGED_PATH_TITLE = "Save Path location";
        public static final String SAVE_DATA_EXISTING_FILES_TITLE = "Detected Save Files";
        public static final String SAVE_DATA_EXISTING_FILES_HELPER = "Managed saves are stored in GameDuck's save directory. Older legacy filenames are detected here too.";
        public static final String SAVE_DATA_NO_ROM_TITLE = "No Battery Save Game Loaded";
        public static final String SAVE_DATA_NO_GAME_BADGE = "No Game";
        public static final String SAVE_DATA_UNSUPPORTED_BADGE = "No Save RAM";
        public static final String SAVE_DATA_READY_BADGE = "Save Ready";
        public static final String SAVE_DATA_EMPTY_BADGE = "No Save File";
        public static final String SAVE_DATA_IMPORT_BUTTON = "Import";
        public static final String SAVE_DATA_EXPORT_BUTTON = "Export";
        public static final String SAVE_DATA_DELETE_BUTTON = "Delete";
        public static final String SAVE_IMPORT_DIALOG_TITLE = "Import save data";
        public static final String SAVE_EXPORT_DIALOG_TITLE = "Export save data";
        public static final String SAVE_IMPORT_FAILED_TITLE = "Failed to import save data";
        public static final String SAVE_EXPORT_FAILED_TITLE = "Failed to export save data";
        public static final String SAVE_DELETE_FAILED_TITLE = "Failed to delete save data";
        public static final String SAVE_DELETE_CONFIRM_TITLE = "Delete Save Data";
        public static final String SAVE_DETAILS_LIVE_SIZE_TITLE = "Live Save Size";
        public static final String SAVE_DETAILS_EXPECTED_SIZE_TITLE = "Expected Cartridge Save Size";
        public static final String SAVE_DETAILS_NONE = "No managed save file has been created yet";
        public static final String SAVE_DETAILS_NOT_AVAILABLE = "Not available";
        public static final String SAVE_DETAILS_UNKNOWN_TIME = "Unknown";
        public static final String SAVE_DELETE_WARNING = "Deleting clears the live cartridge RAM and removes the managed .sav file";

        // -----------------------------------------------------------------
        // Emulation tab: DMG and CGB boot ROM configuration.
        // -----------------------------------------------------------------
        public static final String BOOT_ROM_REQUIRED_TITLE = "Boot ROM Required";
        public static final String USE_DMG_BOOT_ROM_CHECKBOX = "Use Game Boy Boot Rom";
        public static final String DMG_BOOT_SEQUENCE_TITLE = "Game Boy Boot Rom";
        public static final String DMG_BOOT_SEQUENCE_HELPER = "Run the installed boot rom on startup";
        public static final String INSTALLED_BOOT_ROM_TITLE = "Installed Boot ROM";
        public static final String INSTALLED_BOOT_ROM_HELPER = "";
        public static final String MANAGED_PATH_TITLE = "Managed Path";
        public static final String INSERT_BOOT_ROM_BUTTON = "Insert GB Boot ROM";
        public static final String REMOVE_BOOT_ROM_BUTTON = "Remove GB Boot ROM";
        public static final String USE_CGB_BOOT_ROM_CHECKBOX = "Use Game Boy Color boot rom";
        public static final String BOOT_ROM_FILE_DIALOG_TITLE = "Select a boot ROM";
        public static final String CGB_BOOT_SEQUENCE_TITLE = "GBC Boot Sequence";
        public static final String CGB_BOOT_SEQUENCE_HELPER = "Run CGB-capable cartridges through an installed GBC boot ROM instead of jumping straight to the post-boot CGB hardware state";
        public static final String INSTALLED_CGB_BOOT_ROM_TITLE = "Installed GBC Boot ROM";
        public static final String INSTALLED_CGB_BOOT_ROM_HELPER = "Stored in GameDuck's managed data folder separately from the DMG boot ROM";
        public static final String MANAGED_CGB_PATH_TITLE = "Managed GBC Path";
        public static final String INSERT_CGB_BOOT_ROM_BUTTON = "Insert GBC Boot ROM";
        public static final String REMOVE_CGB_BOOT_ROM_BUTTON = "Remove GBC Boot ROM";
        public static final String BOOT_ROM_INSTALL_FAILED_TITLE = "Failed to install boot ROM";
        public static final String BOOT_ROM_REMOVE_FAILED_TITLE = "Failed to remove boot ROM";

        // -----------------------------------------------------------------
        // Shared modal button labels and capture helper text.
        // -----------------------------------------------------------------
        public static final String CLOSE_BUTTON = "Close";
        public static final String PRESS_ESCAPE_TO_CANCEL = "Press Escape to cancel";
        public static final String SHORTCUT_CAPTURE_HELPER = "Use modifiers like Ctrl or Shift. Press Escape to cancel.";

        private OptionsWindow() {
        }

        // -----------------------------------------------------------------
        // Palette and theme feedback messages.
        // -----------------------------------------------------------------
        public static String PaletteNameRequiredMessage() {
            return "Enter a palette name first";
        }

        public static String PaletteSavedMessage(String name) {
            return "Palette \"" + name + "\" saved";
        }

        public static String ThemeNameRequiredMessage() {
            return "Enter a theme name first";
        }

        public static String ThemeSavedMessage(String name) {
            return "Theme \"" + name + "\" saved";
        }

        // -----------------------------------------------------------------
        // Palette, color chooser, and rebinding dialog labels.
        // -----------------------------------------------------------------
        public static String EditPaletteToneTooltip(String toneName) {
            return "Edit " + toneName + " Color";
        }

        public static String PaletteToneColorLabel(String toneName) {
            return toneName + " Color";
        }

        public static String ChooseColorTitle(String label) {
            return "Choose " + label;
        }

        public static String GbcColorChooserTitle() {
            return "Choose GBC Palette Color";
        }

        public static String RebindDialogTitle(String buttonName) {
            return "Rebind " + buttonName;
        }

        public static String RebindDialogPrompt(String buttonName) {
            return "Press a key for " + buttonName;
        }

        public static String RebindAllDialogPrompt(String buttonName) {
            return "Press a key for " + buttonName + " (" + PRESS_ESCAPE_TO_CANCEL + ")";
        }

        public static String ShortcutDialogTitle(String shortcutLabel) {
            return "Shortcut for " + shortcutLabel;
        }

        public static String ShortcutDialogPrompt(String shortcutLabel) {
            return "Press a shortcut for " + shortcutLabel;
        }

        public static String ShortcutControllerDialogTitle(String shortcutLabel) {
            return "Controller shortcut for " + shortcutLabel;
        }

        public static String ShortcutControllerDialogPrompt(String shortcutLabel) {
            return "Press a controller input for " + shortcutLabel;
        }

        public static String ControllerRebindDialogTitle(String buttonName) {
            return "Controller binding for " + buttonName;
        }

        public static String ControllerRebindDialogPrompt(String buttonName) {
            return "Press a controller input for " + buttonName;
        }

        public static String ControllerRebindAllDialogPrompt(String buttonName) {
            return "Press a controller input for " + buttonName + " (" + PRESS_ESCAPE_TO_CANCEL + ")";
        }

        // -----------------------------------------------------------------
        // Sound UI formatting helpers.
        // -----------------------------------------------------------------
        public static String AudioChainItemLabel(int index, String presetLabel) {
            return (index + 1) + ". " + presetLabel;
        }

        public static String AudioChainItemLabel(int index, String presetLabel, int intensityPercent) {
            return (index + 1) + ". " + presetLabel + " (" + intensityPercent + "%)";
        }

        public static String ChannelVolumeLabel(int volume) {
            return volume + "%";
        }

        public static String PercentValue(int value) {
            return value + "%";
        }

        public static String ShaderStatusSummary(int shaderCount, int errorCount) {
            String shaderLabel = shaderCount == 1 ? "1 shader loaded" : shaderCount + " shaders loaded";
            String errorLabel = errorCount == 1 ? "1 load error" : errorCount + " load errors";
            return shaderLabel + " | " + errorLabel;
        }

        public static String BorderStatusSummary(int borderCount, int errorCount) {
            String borderLabel = borderCount == 1 ? "1 border loaded" : borderCount + " borders loaded";
            String errorLabel = errorCount == 1 ? "1 load error" : errorCount + " load errors";
            return borderLabel + " | " + errorLabel;
        }

        public static String ChannelName(int channelIndex) {
            return switch (channelIndex) {
                case 0 -> "Pulse 1";
                case 1 -> "Pulse 2";
                case 2 -> "Wave";
                case 3 -> "Noise";
                default -> "Channel";
            };
        }

        // -----------------------------------------------------------------
        // Boot ROM requirement prompts.
        // -----------------------------------------------------------------
        public static String DmgBootRomRequiredMessage() {
            return "Install a 256-byte DMG boot ROM first. GameDuck does not bundle the original Nintendo boot ROM.";
        }

        public static String CgbBootRomRequiredMessage() {
            return "Install a 2048-byte mapped or 2304-byte full-dump CGB boot ROM first. GameDuck does not bundle the original Nintendo boot ROM.";
        }

        // -----------------------------------------------------------------
        // Save data messages for import/export/delete flows.
        // -----------------------------------------------------------------
        public static String SaveImportSuccessMessage(String gameName, int importedBytes) {
            return "Imported " + importedBytes + " bytes into " + gameName
                    + ". Restart the game if it only reads save data during boot.";
        }

        public static String SaveExportSuccessMessage(String destinationPath) {
            return "Exported save data to:\n" + destinationPath;
        }

        public static String SaveDeleteConfirmMessage(String gameName) {
            return "Delete the managed save data for " + gameName + "?\n\n" + SAVE_DELETE_WARNING;
        }

        public static String SaveDeletedMessage(String gameName) {
            return "Deleted the managed save data for " + gameName;
        }

        // -----------------------------------------------------------------
        // Save manager formatting helpers.
        // -----------------------------------------------------------------
        public static String SaveFileEntrySummary(String label, long sizeBytes, String modifiedText) {
            return label + "  •  " + FormatByteSize(sizeBytes) + "  •  " + modifiedText;
        }

        public static String FormatByteSize(long sizeBytes) {
            if (sizeBytes < 0) {
                return SAVE_DETAILS_NOT_AVAILABLE;
            }
            return sizeBytes + " bytes";
        }

        public static String SaveManagerTrackedGamesBadge(int count) {
            return count == 1 ? "1 Tracked Game" : count + " Tracked Games";
        }

        // -----------------------------------------------------------------
        // Library info actions.
        // -----------------------------------------------------------------
        public static String InfoArtworkFetchedMessage(String gameName) {
            return "Fetched artwork for " + gameName;
        }

        public static String InfoArtworkMissingMessage(String gameName) {
            return "No artwork was found for " + gameName;
        }

        public static String InfoDeleteRomConfirmMessage(String gameName) {
            return "Delete " + gameName + " from the managed library?";
        }

        public static String InfoDeleteRomSuccessMessage(String gameName) {
            return "Deleted " + gameName + " from the managed library";
        }

        public static String SaveManagerLiveSizeSummary(String liveSize, String expectedSize) {
            return "Live " + liveSize + " | Expected " + expectedSize;
        }

        // -----------------------------------------------------------------
        // Save-state manager formatting helpers.
        // -----------------------------------------------------------------
        public static String SaveStateManagerTrackedGamesBadge(int count) {
            return count == 1 ? "1 Tracked ROM" : count + " Tracked ROMs";
        }

        public static String SaveStateSlotSummary(int filledSlots) {
            return filledSlots == 1 ? "1 Saved Slot" : filledSlots + " Saved Slots";
        }

        public static String SaveStateSlotStatus(boolean exists) {
            return exists ? SAVE_STATE_MANAGER_SLOT_PRESENT : SAVE_STATE_MANAGER_SLOT_EMPTY;
        }

        // -----------------------------------------------------------------
        // Save-state manager messages for import/export/delete/move flows.
        // -----------------------------------------------------------------
        public static String SaveStateDeleteConfirmMessage(String gameName, String slotLabel) {
            return "Delete " + slotLabel + " for " + gameName + "?";
        }

        public static String SaveStateDeleteAllConfirmMessage(String gameName) {
            return "Delete every managed save state for " + gameName + "?";
        }

        public static String SaveStateDeletedMessage(String gameName, String slotLabel) {
            return "Deleted " + slotLabel + " for " + gameName;
        }

        public static String SaveStateDeletedAllMessage(String gameName) {
            return "Deleted all managed save states for " + gameName;
        }

        public static String SaveStateExportSuccessMessage(String destinationPath) {
            return "Exported save state to:\n" + destinationPath;
        }

        public static String SaveStateImportSuccessMessage(String gameName, String slotLabel) {
            return "Imported a save state into " + slotLabel + " for " + gameName;
        }

        public static String SaveStateMoveSuccessMessage(String gameName, String sourceSlotLabel,
                String targetSlotLabel) {
            return "Moved " + sourceSlotLabel + " to " + targetSlotLabel + " for " + gameName;
        }

        public static String ShaderFolderPathMessage(String pathText) {
            return "Shader folder:\n" + pathText;
        }

        public static String ShaderFolderOpenFailedMessage(String pathText) {
            return "Unable to open the shader folder automatically.\n\nPath:\n" + pathText;
        }

        // -----------------------------------------------------------------
        // Lookup helpers for palette and control metadata.
        // -----------------------------------------------------------------
        public static String GbcPaletteButtonLabel(int colourIndex) {
            return "C" + colourIndex;
        }

        public static String PaletteToneDescription(int index) {
            return switch (index) {
                case 0 -> "Base screen tone";
                case 1 -> "Light highlight tone";
                case 2 -> "Mid-tone detail shade";
                case 3 -> "Dark outline shade";
                default -> "";
            };
        }

        public static String DmgButtonName(String buttonId) {
            return switch (buttonId) {
                case "A" -> "A";
                case "B" -> "B";
                case "UP" -> "Up";
                case "DOWN" -> "Down";
                case "LEFT" -> "Left";
                case "RIGHT" -> "Right";
                case "START" -> "Start";
                case "SELECT" -> "Select";
                default -> "Button";
            };
        }

        public static String DmgControlHelper(String buttonId) {
            return switch (buttonId) {
                case "A" -> "Primary face button";
                case "B" -> "Secondary face button";
                case "UP" -> "Move up";
                case "DOWN" -> "Move down";
                case "LEFT" -> "Move left";
                case "RIGHT" -> "Move right";
                case "START" -> "Pause or start";
                case "SELECT" -> "Secondary system action";
                default -> "";
            };
        }
    }
}
