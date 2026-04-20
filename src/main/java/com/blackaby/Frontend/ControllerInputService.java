package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Misc.AppShortcut;
import com.blackaby.Misc.ControllerBinding;
import com.blackaby.Misc.Settings;
import com.github.strikerx3.jxinput.XInputAxes;
import com.github.strikerx3.jxinput.XInputButtons;
import com.github.strikerx3.jxinput.XInputComponents;
import com.github.strikerx3.jxinput.XInputDevice;
import com.github.strikerx3.jxinput.exceptions.XInputNotLoadedException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import javax.swing.SwingUtilities;

/**
 * Discovers, polls, and rescans generic host controllers.
 */
public final class ControllerInputService {

    /**
     * Lightweight descriptor exposed to the UI.
     *
     * @param id          stable controller identifier
     * @param displayName display name shown to the user
     */
    public record ControllerDevice(String id, String displayName) {
    }

    public record ControllerPollSnapshot(EnumSet<GBButton> boundButtons, EnumSet<AppShortcut> pressedShortcuts) {
    }

    public record ControllerLiveSnapshot(Optional<ControllerDevice> activeController, List<ControllerBinding> activeInputs,
            EnumSet<GBButton> boundButtons, EnumSet<AppShortcut> pressedShortcuts) {
        static ControllerLiveSnapshot Empty(Optional<ControllerDevice> activeController) {
            return new ControllerLiveSnapshot(activeController, List.of(),
                    EnumSet.noneOf(GBButton.class),
                    EnumSet.noneOf(AppShortcut.class));
        }
    }

    enum ComponentKind {
        BUTTON,
        AXIS,
        POV
    }

    @FunctionalInterface
    interface ControllerPoller {
        boolean poll();
    }

    @FunctionalInterface
    interface ComponentSnapshotPoller {
        void pollInto(Map<String, Float> componentValues);
    }

    @FunctionalInterface
    interface ControllerScanner {
        ScanResult scan();
    }

    static record ComponentHandle(String id, ComponentKind kind) {
    }

    static record ControllerHandle(ControllerDevice device, ControllerPoller poller,
            ComponentSnapshotPoller componentPoller, List<ComponentHandle> components) {
    }

    static record ScanResult(List<ControllerHandle> controllers, String initialisationError) {
    }

    private record CachedLiveSnapshot(String controllerId, long capturedAtMillis, ControllerLiveSnapshot snapshot) {
    }

    private record JInputComponentReader(String id, Object component, Method getPollDataMethod) {
    }

    private record IndexedComponents(List<ComponentHandle> handles, List<JInputComponentReader> readers) {
    }

    record WindowsControllerDescriptor(String name, String deviceClass) {
    }

    private static final class WindowsControllerProbeState {
        private String deviceDescription;
        private String className;
        private String status;

        void acceptLine(String line, List<WindowsControllerDescriptor> descriptors, LinkedHashSet<String> descriptorKeys) {
            if (line == null || line.isBlank()) {
                flush(descriptors, descriptorKeys);
                return;
            }

            int separatorIndex = line.indexOf(':');
            if (separatorIndex < 0) {
                return;
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            switch (key) {
                case "Device Description" -> deviceDescription = value;
                case "Class Name" -> className = value;
                case "Status" -> status = value;
                default -> {
                }
            }
        }

        void flush(List<WindowsControllerDescriptor> descriptors, LinkedHashSet<String> descriptorKeys) {
            String description = deviceDescription == null ? "" : deviceDescription.trim();
            String deviceClassName = className == null ? "" : className.trim();
            String deviceStatus = status == null ? "" : status.trim();

            if ("Started".equalsIgnoreCase(deviceStatus)
                    && IsFriendlyControllerName(description)
                    && IsRelevantWindowsControllerClass(deviceClassName)) {
                String key = description + "|" + deviceClassName;
                if (descriptorKeys.add(key)) {
                    descriptors.add(new WindowsControllerDescriptor(description, deviceClassName));
                }
            }

            deviceDescription = null;
            className = null;
            status = null;
        }
    }

    private static final ControllerInputService instance = new ControllerInputService();
    private static final long rescanIntervalMillis = 2000L;
    private static final long uiSnapshotMaxAgeMillis = 60L;
    private static final long mapperSnapshotMaxAgeMillis = 20L;
    private static final String nativeVersion = "2.0.10";
    private static final String controllerEnvironmentClassName = "net.java.games.input.ControllerEnvironment";
    private static final String directInputPluginClassName = "net.java.games.input.DirectInputEnvironmentPlugin";
    private static final String rawInputPluginClassName = "net.java.games.input.RawInputEnvironmentPlugin";
    private static final String winTabPluginClassName = "net.java.games.input.WinTabEnvironmentPlugin";
    private static final String linuxPluginClassName = "net.java.games.input.LinuxEnvironmentPlugin";
    private static final String osxPluginClassName = "net.java.games.input.OSXEnvironmentPlugin";
    private static final List<String> windowsControllerClasses = List.of("Bluetooth", "HIDClass", "USB",
            "XnaComposite");
    private static final String povComponentId = "pov";
    private static final String xInputPrefix = "xinput|";
    private static final float povUpLeft = 0.125f;
    private static final float povUp = 0.25f;
    private static final float povUpRight = 0.375f;
    private static final float povRight = 0.5f;
    private static final float povDownRight = 0.625f;
    private static final float povDown = 0.75f;
    private static final float povDownLeft = 0.875f;
    private static final float povLeft = 1.0f;
    private static final List<ComponentHandle> xInputComponents = List.of(
            new ComponentHandle("0", ComponentKind.BUTTON),
            new ComponentHandle("1", ComponentKind.BUTTON),
            new ComponentHandle("2", ComponentKind.BUTTON),
            new ComponentHandle("3", ComponentKind.BUTTON),
            new ComponentHandle("4", ComponentKind.BUTTON),
            new ComponentHandle("5", ComponentKind.BUTTON),
            new ComponentHandle("6", ComponentKind.BUTTON),
            new ComponentHandle("7", ComponentKind.BUTTON),
            new ComponentHandle("8", ComponentKind.BUTTON),
            new ComponentHandle("9", ComponentKind.BUTTON),
            new ComponentHandle(povComponentId, ComponentKind.POV),
            new ComponentHandle("x", ComponentKind.AXIS),
            new ComponentHandle("y", ComponentKind.AXIS),
            new ComponentHandle("rx", ComponentKind.AXIS),
            new ComponentHandle("ry", ComponentKind.AXIS),
            new ComponentHandle("z", ComponentKind.AXIS),
            new ComponentHandle("rz", ComponentKind.AXIS));

    private final Object pollLock = new Object();
    private final LongSupplier currentTimeMillis;
    private final ControllerScanner testScanner;
    private final Map<String, Float> polledComponentValues = new HashMap<>();
    private final EnumMap<GBButton, ControllerBinding> cachedControllerBindings = new EnumMap<>(GBButton.class);
    private final EnumMap<AppShortcut, ControllerBinding> cachedShortcutBindings = new EnumMap<>(AppShortcut.class);
    private final AtomicBoolean uiRescanQueued = new AtomicBoolean();
    private volatile long lastScanTimestamp;
    private volatile String initialisationError;
    private boolean nativeLibrariesReady;
    private boolean missingRuntimeReported;
    private boolean initialScanComplete;
    private long cachedControllerBindingsVersion = -1L;
    private long cachedShortcutBindingsVersion = -1L;
    private List<ControllerHandle> controllerHandles = List.of();
    private ControllerHandle activeController;
    private CachedLiveSnapshot cachedLiveSnapshot;
    private ExecutorService uiRescanExecutor;

    private ControllerInputService() {
        this(System::currentTimeMillis, null);
    }

    ControllerInputService(LongSupplier currentTimeMillis, ControllerScanner testScanner) {
        this.currentTimeMillis = currentTimeMillis == null ? System::currentTimeMillis : currentTimeMillis;
        this.testScanner = testScanner;
    }

    /**
     * Returns the shared controller service.
     *
     * @return shared service instance
     */
    public static ControllerInputService Shared() {
        return instance;
    }

    /**
     * Returns the available controllers discovered during the latest scan.
     *
     * @return discovered controller devices
     */
    public List<ControllerDevice> GetAvailableControllers() {
        synchronized (pollLock) {
            EnsureInitialScanLocked();
            return SnapshotDevicesLocked();
        }
    }

    /**
     * Returns the currently active controller if one is selected and connected.
     *
     * @return active controller descriptor
     */
    public Optional<ControllerDevice> GetActiveController() {
        synchronized (pollLock) {
            EnsureInitialScanLocked();
            ControllerHandle handle = SelectActiveControllerLocked();
            return handle == null ? Optional.empty() : Optional.of(handle.device());
        }
    }

    /**
     * Returns any initialisation error encountered while setting up controller
     * support.
     *
     * @return initialisation error, or {@code null} if setup succeeded
     */
    public String GetInitialisationError() {
        return initialisationError;
    }

    /**
     * Forces an immediate controller rescan.
     */
    public void RefreshControllers() {
        synchronized (pollLock) {
            if (shouldDeferUiRescan(SwingUtilities.isEventDispatchThread(), testScanner != null)) {
                ScheduleUiRescanLocked(true);
            } else {
                ScanControllersLocked();
            }
        }
    }

    /**
     * Polls the active controller and returns the currently pressed Game Boy
     * buttons.
     *
     * @return pressed emulated buttons
     */
    public EnumSet<GBButton> PollBoundButtons() {
        synchronized (pollLock) {
            if (!Settings.controllerInputEnabled) {
                return EnumSet.noneOf(GBButton.class);
            }
            return CopyButtons(GetLiveSnapshotLocked(uiSnapshotMaxAgeMillis).boundButtons());
        }
    }

    public ControllerPollSnapshot PollInputSnapshot() {
        synchronized (pollLock) {
            if (!Settings.controllerInputEnabled) {
                return new ControllerPollSnapshot(
                        EnumSet.noneOf(GBButton.class),
                        EnumSet.noneOf(AppShortcut.class));
            }

            ControllerLiveSnapshot liveSnapshot = GetLiveSnapshotLocked(0L);
            return new ControllerPollSnapshot(
                    CopyButtons(liveSnapshot.boundButtons()),
                    CopyShortcuts(liveSnapshot.pressedShortcuts()));
        }
    }

    public ControllerLiveSnapshot PollLiveSnapshot() {
        synchronized (pollLock) {
            return CopyLiveSnapshot(GetLiveSnapshotLocked(uiSnapshotMaxAgeMillis));
        }
    }

    /**
     * Polls the active controller and returns the currently active raw bindings.
     *
     * @return active raw inputs
     */
    public List<ControllerBinding> PollActiveInputs() {
        synchronized (pollLock) {
            return List.copyOf(GetLiveSnapshotLocked(mapperSnapshotMaxAgeMillis).activeInputs());
        }
    }

    private ControllerLiveSnapshot GetLiveSnapshotLocked(long maxAgeMillis) {
        EnsureInitialScanLocked();
        ControllerHandle selectedHandle = SelectActiveControllerLocked();
        Optional<ControllerDevice> selectedDevice = selectedHandle == null
                ? Optional.empty()
                : Optional.of(selectedHandle.device());

        if (!Settings.controllerInputEnabled || selectedHandle == null) {
            return ControllerLiveSnapshot.Empty(selectedDevice);
        }

        RefreshBindingCachesIfNeededLocked();
        long now = currentTimeMillis.getAsLong();
        if (cachedLiveSnapshot != null
                && selectedHandle.device().id().equals(cachedLiveSnapshot.controllerId())
                && (now - cachedLiveSnapshot.capturedAtMillis()) <= maxAgeMillis) {
            return cachedLiveSnapshot.snapshot();
        }

        ControllerHandle handle = PollHotPathControllerLocked();
        Optional<ControllerDevice> activeDevice = handle == null
                ? Optional.empty()
                : Optional.of(handle.device());
        if (handle == null) {
            ControllerLiveSnapshot emptySnapshot = ControllerLiveSnapshot.Empty(activeDevice);
            cachedLiveSnapshot = new CachedLiveSnapshot("", now, emptySnapshot);
            return emptySnapshot;
        }

        Map<String, Float> componentValues = PollComponentValues(handle, polledComponentValues);
        ControllerLiveSnapshot liveSnapshot = new ControllerLiveSnapshot(
                activeDevice,
                ResolveActiveInputs(handle.components(), componentValues),
                ResolvePressedButtons(componentValues),
                ResolvePressedShortcuts(componentValues));
        cachedLiveSnapshot = new CachedLiveSnapshot(handle.device().id(), now, liveSnapshot);
        return liveSnapshot;
    }

    private ControllerHandle PollHotPathControllerLocked() {
        ControllerHandle handle = SelectActiveControllerLocked();
        if (handle == null) {
            return null;
        }

        if (PollController(handle)) {
            return handle;
        }

        List<ControllerHandle> remainingControllers = new ArrayList<>(controllerHandles);
        remainingControllers.remove(handle);
        controllerHandles = List.copyOf(remainingControllers);
        if (activeController == handle) {
            activeController = null;
        }
        InvalidateLiveSnapshotLocked();

        handle = SelectActiveControllerLocked();
        return handle != null && PollController(handle) ? handle : null;
    }

    private void EnsureInitialScanLocked() {
        if (initialScanComplete) {
            return;
        }

        if (shouldDeferUiRescan(SwingUtilities.isEventDispatchThread(), testScanner != null)) {
            ScheduleUiRescanLocked(true);
        } else {
            ScanControllersLocked();
        }
    }

    static boolean shouldDeferUiRescan(boolean onEdt, boolean testScannerPresent) {
        return onEdt && !testScannerPresent;
    }

    private void ScheduleUiRescanLocked(boolean force) {
        if (!uiRescanQueued.compareAndSet(false, true)) {
            return;
        }

        EnsureUiRescanExecutorLocked().execute(() -> {
            synchronized (pollLock) {
                try {
                    if (force || (currentTimeMillis.getAsLong() - lastScanTimestamp) >= rescanIntervalMillis) {
                        ScanControllersLocked();
                    }
                } finally {
                    uiRescanQueued.set(false);
                }
            }
        });
    }

    private ExecutorService EnsureUiRescanExecutorLocked() {
        if (uiRescanExecutor == null || uiRescanExecutor.isShutdown()) {
            uiRescanExecutor = Executors.newSingleThreadExecutor(run -> {
                Thread thread = new Thread(run, "gameduck-controller-ui-scan");
                thread.setDaemon(true);
                return thread;
            });
        }
        return uiRescanExecutor;
    }

    private void RefreshBindingCachesIfNeededLocked() {
        long controllerBindingsVersion = Settings.controllerBindings.Version();
        if (controllerBindingsVersion != cachedControllerBindingsVersion) {
            cachedControllerBindings.clear();
            cachedControllerBindings.putAll(Settings.controllerBindings.SnapshotBindings());
            cachedControllerBindingsVersion = controllerBindingsVersion;
            InvalidateLiveSnapshotLocked();
        }

        long shortcutBindingsVersion = Settings.appShortcutControllerBindings.Version();
        if (shortcutBindingsVersion != cachedShortcutBindingsVersion) {
            cachedShortcutBindings.clear();
            cachedShortcutBindings.putAll(Settings.appShortcutControllerBindings.SnapshotBindings());
            cachedShortcutBindingsVersion = shortcutBindingsVersion;
            InvalidateLiveSnapshotLocked();
        }
    }

    private EnumSet<GBButton> ResolvePressedButtons(Map<String, Float> componentValues) {
        float deadzone = Settings.controllerDeadzonePercent / 100f;
        EnumSet<GBButton> pressedButtons = EnumSet.noneOf(GBButton.class);
        for (GBButton button : GBButton.values()) {
            ControllerBinding binding = cachedControllerBindings.get(button);
            if (binding != null && binding.Matches(componentValues, deadzone)) {
                pressedButtons.add(button);
            }
        }
        return pressedButtons;
    }

    private EnumSet<AppShortcut> ResolvePressedShortcuts(Map<String, Float> componentValues) {
        float deadzone = Math.max(Settings.controllerDeadzonePercent / 100f, 0.35f);
        EnumSet<AppShortcut> pressedShortcuts = EnumSet.noneOf(AppShortcut.class);
        for (AppShortcut shortcut : AppShortcut.values()) {
            ControllerBinding binding = cachedShortcutBindings.get(shortcut);
            if (binding != null && binding.Matches(componentValues, deadzone)) {
                pressedShortcuts.add(shortcut);
            }
        }
        return pressedShortcuts;
    }

    private List<ControllerBinding> ResolveActiveInputs(List<ComponentHandle> components, Map<String, Float> componentValues) {
        List<ControllerBinding> activeInputs = new ArrayList<>();
        float deadzone = Math.max(Settings.controllerDeadzonePercent / 100f, 0.35f);
        for (ComponentHandle component : components) {
            Float value = componentValues.get(component.id());
            if (value == null) {
                continue;
            }

            if (component.kind() == ComponentKind.BUTTON && value >= 0.5f) {
                activeInputs.add(ControllerBinding.Button(component.id()));
                continue;
            }

            if (component.kind() == ComponentKind.POV) {
                AddPovBindings(activeInputs, value);
                continue;
            }

            if (value >= deadzone) {
                activeInputs.add(ControllerBinding.Axis(component.id(), true));
            } else if (value <= -deadzone) {
                activeInputs.add(ControllerBinding.Axis(component.id(), false));
            }
        }

        activeInputs.sort(Comparator.comparing(ControllerBinding::ToDisplayText));
        return List.copyOf(activeInputs);
    }

    private void ScanControllersLocked() {
        if (testScanner != null) {
            ScanResult scanResult = testScanner.scan();
            controllerHandles = scanResult == null || scanResult.controllers() == null
                    ? List.of()
                    : List.copyOf(scanResult.controllers());
            activeController = null;
            SelectActiveControllerLocked();
            initialisationError = scanResult == null ? null : scanResult.initialisationError();
            lastScanTimestamp = currentTimeMillis.getAsLong();
            initialScanComplete = true;
            InvalidateLiveSnapshotLocked();
            return;
        }

        List<ControllerHandle> discoveredControllers = new ArrayList<>();
        List<String> backendErrors = new ArrayList<>();

        DiscoverXInputControllers(discoveredControllers, backendErrors);
        DiscoverJInputControllers(discoveredControllers, backendErrors);

        controllerHandles = List.copyOf(ApplyFriendlyControllerNames(
                discoveredControllers,
                LoadWindowsFriendlyControllerDescriptors()));
        activeController = null;
        SelectActiveControllerLocked();
        initialisationError = DetermineInitialisationError(backendErrors, discoveredControllers.isEmpty());
        lastScanTimestamp = currentTimeMillis.getAsLong();
        initialScanComplete = true;
        InvalidateLiveSnapshotLocked();
    }

    private void DiscoverXInputControllers(List<ControllerHandle> discoveredControllers, List<String> backendErrors) {
        if (!IsWindows()) {
            return;
        }

        try {
            if (!XInputDevice.isAvailable()) {
                backendErrors.add("XInput support is unavailable on this system.");
                return;
            }

            for (XInputDevice device : XInputDevice.getAllDevices()) {
                if (device == null) {
                    continue;
                }

                try {
                    if (device.poll() && device.isConnected()) {
                        discoveredControllers.add(BuildXInputHandle(device));
                    }
                } catch (RuntimeException ignored) {
                    // Skip one broken XInput slot without breaking the rest of the scan.
                }
            }
        } catch (XInputNotLoadedException | LinkageError exception) {
            backendErrors.add("XInput support could not be loaded. Restart GameDuck after rebuilding dependencies.");
        } catch (RuntimeException exception) {
            backendErrors.add("XInput support could not be initialised.");
        }
    }

    private ControllerHandle BuildXInputHandle(XInputDevice device) {
        return new ControllerHandle(
                BuildXInputDevice(device),
                () -> device.poll() && device.isConnected(),
                componentValues -> PollXInputComponentValues(device, componentValues),
                xInputComponents);
    }

    static List<ControllerHandle> ApplyFriendlyControllerNames(List<ControllerHandle> discoveredControllers,
            List<WindowsControllerDescriptor> friendlyDescriptors) {
        if (discoveredControllers == null || discoveredControllers.isEmpty()) {
            return List.of();
        }

        if (friendlyDescriptors.isEmpty()) {
            return List.copyOf(discoveredControllers);
        }

        List<ControllerHandle> renamedHandles = new ArrayList<>(discoveredControllers.size());
        for (ControllerHandle handle : discoveredControllers) {
            if (handle == null || handle.device() == null) {
                renamedHandles.add(handle);
                continue;
            }

            if (!ShouldReplaceWithFriendlyName(handle.device().displayName())) {
                renamedHandles.add(handle);
                continue;
            }

            String friendlyName = ResolveFriendlyControllerName(handle.device(), friendlyDescriptors);
            if (friendlyName == null || friendlyName.isBlank()) {
                renamedHandles.add(handle);
                continue;
            }

            String deviceId = handle.device().id();
            ControllerDevice renamedDevice = new ControllerDevice(deviceId, friendlyName);
            renamedHandles.add(new ControllerHandle(
                    renamedDevice,
                    handle.poller(),
                    handle.componentPoller(),
                    handle.components()));
        }
        return List.copyOf(renamedHandles);
    }

    private List<WindowsControllerDescriptor> LoadWindowsFriendlyControllerDescriptors() {
        if (!IsWindows()) {
            return List.of();
        }

        LinkedHashSet<String> descriptorKeys = new LinkedHashSet<>();
        List<WindowsControllerDescriptor> descriptors = new ArrayList<>();
        for (String controllerClass : windowsControllerClasses) {
            CollectWindowsControllerDescriptorsForClass(controllerClass, descriptors, descriptorKeys);
        }
        return List.copyOf(descriptors);
    }

    private void CollectWindowsControllerDescriptorsForClass(String controllerClass,
            List<WindowsControllerDescriptor> descriptors, LinkedHashSet<String> descriptorKeys) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "pnputil",
                    "/enum-devices",
                    "/connected",
                    "/class",
                    controllerClass)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return;
            }
            if (process.exitValue() != 0) {
                return;
            }

            WindowsControllerProbeState probeState = new WindowsControllerProbeState();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : output.lines().toList()) {
                probeState.acceptLine(line, descriptors, descriptorKeys);
            }
            probeState.flush(descriptors, descriptorKeys);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String ResolveFriendlyControllerName(ControllerDevice device,
            List<WindowsControllerDescriptor> friendlyDescriptors) {
        if (device == null || friendlyDescriptors == null || friendlyDescriptors.isEmpty()) {
            return null;
        }

        String deviceId = device.id() == null ? "" : device.id();
        if (deviceId.startsWith(xInputPrefix)) {
            String xinputName = ResolveXInputFriendlyName(friendlyDescriptors);
            return xinputName == null || xinputName.isBlank() ? null : xinputName;
        }

        String[] idParts = deviceId.split("\\|", 4);
        String portType = idParts.length > 1 ? idParts[1].trim().toLowerCase(Locale.ROOT) : "";
        List<String> candidates = new ArrayList<>();
        for (WindowsControllerDescriptor descriptor : friendlyDescriptors) {
            String deviceClass = descriptor.deviceClass() == null ? "" : descriptor.deviceClass().trim().toLowerCase(Locale.ROOT);
            if (portType.contains("bluetooth") && !"bluetooth".equals(deviceClass)) {
                continue;
            }
            if (portType.contains("usb") && !("usb".equals(deviceClass) || "xnacomposite".equals(deviceClass))) {
                continue;
            }
            if (LooksLikeXInputFriendlyName(descriptor.name(), descriptor.deviceClass())) {
                continue;
            }
            candidates.add(descriptor.name());
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static String ResolveXInputFriendlyName(List<WindowsControllerDescriptor> friendlyDescriptors) {
        for (WindowsControllerDescriptor descriptor : friendlyDescriptors) {
            if ("xnacomposite".equalsIgnoreCase(descriptor.deviceClass())
                    && IsFriendlyControllerName(descriptor.name())) {
                return descriptor.name();
            }
        }
        for (WindowsControllerDescriptor descriptor : friendlyDescriptors) {
            if (LooksLikeXInputFriendlyName(descriptor.name(), descriptor.deviceClass())) {
                return descriptor.name();
            }
        }
        return null;
    }

    private static boolean LooksLikeXInputFriendlyName(String name, String deviceClass) {
        String lowerName = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String lowerClass = deviceClass == null ? "" : deviceClass.trim().toLowerCase(Locale.ROOT);
        return "xnacomposite".equals(lowerClass)
                || lowerName.contains("xbox")
                || lowerName.contains("xinput")
                || lowerName.contains("controller for windows");
    }

    private static boolean ShouldReplaceWithFriendlyName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return true;
        }

        String lower = displayName.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("xinput controller ")
                || lower.contains("hid-compliant")
                || lower.contains("game controller")
                || lower.contains("controller for windows")
                || lower.contains("xinput-compatible")
                || lower.contains("usb input device");
    }

    private static boolean IsFriendlyControllerName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (!(lower.contains("controller")
                || lower.contains("gamepad")
                || lower.contains("game pad")
                || lower.contains("joystick")
                || lower.contains("8bitdo")
                || lower.contains("xbox")
                || lower.contains("snes")
                || lower.contains("n64"))) {
            return false;
        }

        return !lower.contains("hid-compliant")
                && !lower.contains("usb input device")
                && !lower.contains("bluetooth hid device")
                && !lower.contains("xinput-compatible")
                && !lower.contains("host controller")
                && !lower.contains("system controller");
    }

    private static boolean IsRelevantWindowsControllerClass(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        String lower = className.trim().toLowerCase(Locale.ROOT);
        return "bluetooth".equals(lower)
                || "hidclass".equals(lower)
                || "usb".equals(lower)
                || "xnacomposite".equals(lower);
    }

    private void DiscoverJInputControllers(List<ControllerHandle> discoveredControllers, List<String> backendErrors) {
        List<String> scanWarnings = new ArrayList<>();
        try {
            EnsureRuntimeAvailable();
            EnsureNativeLibrariesLocked();
            Object environment = ResetEnvironmentCacheAndGetEnvironment();
            Method getControllersMethod = ResolveNoArgMethod(environment.getClass(), "getControllers");
            for (Object controller : AsObjectList(Invoke(environment, getControllersMethod))) {
                TryCollectController(controller, discoveredControllers, scanWarnings);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError exception) {
            missingRuntimeReported = true;
            backendErrors.add(
                    "Generic controller runtime not found on the classpath. Reload the project dependencies and restart GameDuck.");
            return;
        } catch (Exception exception) {
            backendErrors.add(exception.getMessage() == null
                    ? "Unable to initialise generic controller support."
                    : exception.getMessage());
            return;
        }

        String pluginError = DescribePluginSupportIssue();
        if (pluginError != null) {
            backendErrors.add(pluginError);
        }
        if (!scanWarnings.isEmpty()) {
            backendErrors.add(scanWarnings.get(0));
        }
    }

    private void TryCollectController(Object controller, List<ControllerHandle> discoveredControllers, List<String> scanWarnings) {
        try {
            CollectControllers(controller, discoveredControllers, scanWarnings);
        } catch (RuntimeException exception) {
            scanWarnings.add("Skipped a controller because it could not be inspected.");
        }
    }

    private void CollectControllers(Object controller, List<ControllerHandle> discoveredControllers, List<String> scanWarnings) {
        if (controller == null) {
            return;
        }

        Method getTypeMethod = ResolveNoArgMethod(controller.getClass(), "getType");
        Method getPortTypeMethod = ResolveNoArgMethod(controller.getClass(), "getPortType");
        Method getPortNumberMethod = ResolveNoArgMethod(controller.getClass(), "getPortNumber");
        Method getNameMethod = ResolveNoArgMethod(controller.getClass(), "getName");
        Method getControllersMethod = ResolveNoArgMethod(controller.getClass(), "getControllers");
        Method getComponentsMethod = ResolveNoArgMethod(controller.getClass(), "getComponents");
        Method pollMethod = ResolveNoArgMethod(controller.getClass(), "poll");

        try {
            String controllerTypeName = String.valueOf(Invoke(controller, getTypeMethod));
            IndexedComponents indexedComponents = IndexComponents(controller, getComponentsMethod, scanWarnings);
            if (ShouldIncludeController(controllerTypeName, indexedComponents.handles())) {
                String portType = String.valueOf(Invoke(controller, getPortTypeMethod));
                String portNumber = String.valueOf(Invoke(controller, getPortNumberMethod));
                String controllerName = String.valueOf(Invoke(controller, getNameMethod));
                discoveredControllers.add(new ControllerHandle(
                        BuildDevice(controllerTypeName, portType, portNumber, controllerName),
                        () -> {
                            Object result = Invoke(controller, pollMethod);
                            return result instanceof Boolean pollResult && pollResult;
                        },
                        componentValues -> PollJInputComponentValues(indexedComponents.readers(), componentValues),
                        indexedComponents.handles()));
            }
        } catch (RuntimeException exception) {
            scanWarnings.add(
                    "Skipped " + SafeControllerName(controller, getNameMethod) + " because it could not be inspected.");
        }

        for (Object child : AsObjectList(Invoke(controller, getControllersMethod))) {
            TryCollectController(child, discoveredControllers, scanWarnings);
        }
    }

    private ControllerHandle SelectActiveControllerLocked() {
        if (activeController != null && controllerHandles.contains(activeController)) {
            return activeController;
        }

        if (controllerHandles.isEmpty()) {
            activeController = null;
            return null;
        }

        String preferredId = Settings.preferredControllerId;
        if (preferredId != null && !preferredId.isBlank()) {
            for (ControllerHandle handle : controllerHandles) {
                if (preferredId.equals(handle.device().id())) {
                    activeController = handle;
                    return handle;
                }
            }
        }

        activeController = controllerHandles.get(0);
        return activeController;
    }

    private List<ControllerDevice> SnapshotDevicesLocked() {
        List<ControllerDevice> devices = new ArrayList<>(controllerHandles.size());
        for (ControllerHandle handle : controllerHandles) {
            devices.add(handle.device());
        }
        return List.copyOf(devices);
    }

    private void EnsureNativeLibrariesLocked() throws IOException {
        if (nativeLibrariesReady) {
            return;
        }

        Path nativeDirectory = Path.of(System.getProperty("java.io.tmpdir"),
                "gameduck-jinput-" + nativeVersion);
        Files.createDirectories(nativeDirectory);

        for (String resourceName : RequiredNativeResources()) {
            Path destination = nativeDirectory.resolve(resourceName);
            if (!Files.exists(destination)) {
                try (InputStream inputStream = ControllerInputService.class.getClassLoader()
                        .getResourceAsStream(resourceName)) {
                    if (inputStream == null) {
                        throw new IOException("Missing controller runtime resource: " + resourceName);
                    }
                    Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        System.setProperty("net.java.games.input.librarypath", nativeDirectory.toAbsolutePath().toString());
        PrimePlatformPluginsLocked();
        nativeLibrariesReady = true;
    }

    private List<String> RequiredNativeResources() throws IOException {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean is64Bit = architecture.contains("64") || architecture.contains("amd64");

        if (operatingSystem.contains("win") && is64Bit) {
            return List.of("jinput-raw_64.dll", "jinput-dx8_64.dll", "jinput-wintab.dll");
        }
        if (operatingSystem.contains("linux") && is64Bit) {
            return List.of("libjinput-linux64.so");
        }
        if (operatingSystem.contains("mac")) {
            return List.of("libjinput-osx.jnilib");
        }

        throw new IOException("Controller support is unavailable on this platform: "
                + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
    }

    private void EnsureRuntimeAvailable() throws ClassNotFoundException {
        if (missingRuntimeReported) {
            throw new ClassNotFoundException(controllerEnvironmentClassName);
        }
        Class.forName(controllerEnvironmentClassName);
    }

    private Object ResetEnvironmentCacheAndGetEnvironment() throws ReflectiveOperationException, ClassNotFoundException {
        Class<?> controllerEnvironmentClass = Class.forName(controllerEnvironmentClassName);
        Field defaultEnvironmentField = controllerEnvironmentClass.getDeclaredField("defaultEnvironment");
        defaultEnvironmentField.setAccessible(true);
        defaultEnvironmentField.set(null, null);
        Method getDefaultEnvironment = controllerEnvironmentClass.getMethod("getDefaultEnvironment");
        return getDefaultEnvironment.invoke(null);
    }

    private String DetermineInitialisationError(List<String> backendErrors, boolean noControllersDiscovered) {
        if (noControllersDiscovered && backendErrors != null && !backendErrors.isEmpty()) {
            return backendErrors.get(0);
        }
        return null;
    }

    private boolean ShouldIncludeController(String typeName, List<ComponentHandle> components) {
        if (components == null || components.isEmpty()) {
            return false;
        }

        String normalisedType = NormaliseTypeName(typeName);
        if (IsSupportedControllerType(normalisedType)) {
            return true;
        }
        if ("keyboard".equals(normalisedType) || "mouse".equals(normalisedType)) {
            return false;
        }
        return HasUsableComponents(components);
    }

    private boolean IsSupportedControllerType(String normalisedType) {
        return "gamepad".equals(normalisedType)
                || "stick".equals(normalisedType)
                || "wheel".equals(normalisedType)
                || "fingerstick".equals(normalisedType);
    }

    private boolean HasUsableComponents(List<ComponentHandle> components) {
        for (ComponentHandle component : components) {
            if (component.kind() == ComponentKind.BUTTON
                    || component.kind() == ComponentKind.AXIS
                    || component.kind() == ComponentKind.POV) {
                return true;
            }
        }
        return false;
    }

    private ControllerDevice BuildDevice(String typeName, String portType, String portNumber, String controllerName) {
        String identifier = typeName + "|" + portType + "|" + portNumber + "|" + controllerName;
        String prettyTypeName = PrettyTypeName(typeName);
        String displayName = prettyTypeName.isBlank()
                ? controllerName
                : controllerName + " (" + prettyTypeName + ")";
        return new ControllerDevice(identifier, displayName);
    }

    private ControllerDevice BuildXInputDevice(XInputDevice device) {
        int playerIndex = device.getPlayerNum();
        String identifier = xInputPrefix + playerIndex;
        return new ControllerDevice(identifier, "XInput Controller " + (playerIndex + 1));
    }

    private IndexedComponents IndexComponents(Object controller, Method getComponentsMethod, List<String> scanWarnings) {
        List<ComponentHandle> handles = new ArrayList<>();
        List<JInputComponentReader> readers = new ArrayList<>();
        for (Object component : AsObjectList(Invoke(controller, getComponentsMethod))) {
            try {
                Method isRelativeMethod = ResolveNoArgMethod(component.getClass(), "isRelative");
                if (Boolean.TRUE.equals(Invoke(component, isRelativeMethod))) {
                    continue;
                }

                Method getIdentifierMethod = ResolveNoArgMethod(component.getClass(), "getIdentifier");
                Object identifier = Invoke(component, getIdentifierMethod);
                Method getNameMethod = ResolveNoArgMethod(identifier.getClass(), "getName");
                String componentId = String.valueOf(Invoke(identifier, getNameMethod));
                ComponentKind componentKind = DetermineComponentKind(identifier, componentId);
                Method getPollDataMethod = ResolveNoArgMethod(component.getClass(), "getPollData");

                handles.add(new ComponentHandle(componentId, componentKind));
                readers.add(new JInputComponentReader(componentId, component, getPollDataMethod));
            } catch (RuntimeException exception) {
                if (scanWarnings != null) {
                    scanWarnings.add("Skipped a controller component because it could not be read.");
                }
            }
        }
        return new IndexedComponents(List.copyOf(handles), List.copyOf(readers));
    }

    private ComponentKind DetermineComponentKind(Object identifier, String componentId) {
        if (povComponentId.equals(componentId)) {
            return ComponentKind.POV;
        }

        String className = identifier == null ? "" : identifier.getClass().getName();
        return className.contains("$Button") ? ComponentKind.BUTTON : ComponentKind.AXIS;
    }

    private String PrettyTypeName(String typeName) {
        String lower = NormaliseTypeName(typeName);
        if (lower.isBlank()) {
            return "";
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String NormaliseTypeName(String typeName) {
        return typeName == null ? "" : typeName.trim().toLowerCase(Locale.ROOT);
    }

    private boolean PollController(ControllerHandle handle) {
        return handle != null && handle.poller().poll();
    }

    private Map<String, Float> PollComponentValues(ControllerHandle handle, Map<String, Float> componentValues) {
        componentValues.clear();
        handle.componentPoller().pollInto(componentValues);
        return componentValues;
    }

    private void PollJInputComponentValues(List<JInputComponentReader> readers, Map<String, Float> componentValues) {
        for (JInputComponentReader reader : readers) {
            Object value = Invoke(reader.component(), reader.getPollDataMethod());
            if (value instanceof Float floatValue) {
                componentValues.put(reader.id(), floatValue);
            }
        }
    }

    private void PollXInputComponentValues(XInputDevice device, Map<String, Float> componentValues) {
        XInputComponents components = device.getComponents();
        XInputButtons buttons = components.getButtons();
        XInputAxes axes = components.getAxes();

        componentValues.put("0", ButtonValue(buttons.a));
        componentValues.put("1", ButtonValue(buttons.b));
        componentValues.put("2", ButtonValue(buttons.x));
        componentValues.put("3", ButtonValue(buttons.y));
        componentValues.put("4", ButtonValue(buttons.lShoulder));
        componentValues.put("5", ButtonValue(buttons.rShoulder));
        componentValues.put("6", ButtonValue(buttons.back));
        componentValues.put("7", ButtonValue(buttons.start));
        componentValues.put("8", ButtonValue(buttons.lThumb));
        componentValues.put("9", ButtonValue(buttons.rThumb));
        componentValues.put(povComponentId, MapXInputDpadValue(axes.dpad));
        componentValues.put("x", axes.lx);
        componentValues.put("y", -axes.ly);
        componentValues.put("rx", axes.rx);
        componentValues.put("ry", -axes.ry);
        componentValues.put("z", axes.lt);
        componentValues.put("rz", axes.rt);
    }

    private void AddPovBindings(List<ControllerBinding> activeInputs, float value) {
        if (Float.compare(value, povUp) == 0) {
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.UP));
        } else if (Float.compare(value, povDown) == 0) {
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.DOWN));
        } else if (Float.compare(value, povLeft) == 0) {
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.LEFT));
        } else if (Float.compare(value, povRight) == 0) {
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.RIGHT));
        } else if (Float.compare(value, povUpLeft) == 0) {
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.UP));
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.LEFT));
        } else if (Float.compare(value, povUpRight) == 0) {
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.UP));
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.RIGHT));
        } else if (Float.compare(value, povDownLeft) == 0) {
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.DOWN));
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.LEFT));
        } else if (Float.compare(value, povDownRight) == 0) {
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.DOWN));
            activeInputs.add(ControllerBinding.Pov(ControllerBinding.Direction.RIGHT));
        }
    }

    private Method ResolveNoArgMethod(Class<?> type, String methodName) {
        try {
            Method method = type.getMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access controller runtime method " + methodName + ".", exception);
        }
    }

    private Object Invoke(Object target, Method method) {
        if (target == null || method == null) {
            return null;
        }

        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Failed to access controller runtime method " + method.getName() + ".", exception);
        }
    }

    private float ButtonValue(boolean pressed) {
        return pressed ? 1f : 0f;
    }

    private float MapXInputDpadValue(int dpadValue) {
        return switch (dpadValue) {
            case XInputAxes.DPAD_UP -> povUp;
            case XInputAxes.DPAD_UP_RIGHT -> povUpRight;
            case XInputAxes.DPAD_RIGHT -> povRight;
            case XInputAxes.DPAD_DOWN_RIGHT -> povDownRight;
            case XInputAxes.DPAD_DOWN -> povDown;
            case XInputAxes.DPAD_DOWN_LEFT -> povDownLeft;
            case XInputAxes.DPAD_LEFT -> povLeft;
            case XInputAxes.DPAD_UP_LEFT -> povUpLeft;
            default -> 0f;
        };
    }

    private void PrimePlatformPluginsLocked() throws IOException {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean is64Bit = architecture.contains("64") || architecture.contains("amd64");

        try {
            if (operatingSystem.contains("win")) {
                PrimePluginLibrary(directInputPluginClassName, is64Bit ? "jinput-dx8_64" : "jinput-dx8");
                PrimePluginLibrary(rawInputPluginClassName, is64Bit ? "jinput-raw_64" : "jinput-raw");
                PrimePluginLibrary(winTabPluginClassName, "jinput-wintab");
                return;
            }
            if (operatingSystem.contains("linux")) {
                PrimePluginLibrary(linuxPluginClassName, is64Bit ? "jinput-linux64" : "jinput-linux");
                return;
            }
            if (operatingSystem.contains("mac")) {
                PrimePluginLibrary(osxPluginClassName, "jinput-osx");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IOException("Unable to prepare controller runtime plugins.", exception);
        }
    }

    private void PrimePluginLibrary(String className, String libraryName) throws ReflectiveOperationException {
        Class<?> pluginClass = Class.forName(className);
        Field supportedField = pluginClass.getDeclaredField("supported");
        supportedField.setAccessible(true);
        supportedField.setBoolean(null, true);

        Method loadLibraryMethod = pluginClass.getDeclaredMethod("loadLibrary", String.class);
        loadLibraryMethod.setAccessible(true);
        loadLibraryMethod.invoke(null, libraryName);
    }

    private String DescribePluginSupportIssue() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (operatingSystem.contains("win")) {
                boolean directSupported = IsPluginSupported(directInputPluginClassName);
                boolean rawSupported = IsPluginSupported(rawInputPluginClassName);
                boolean winTabSupported = IsPluginSupported(winTabPluginClassName);
                if (!directSupported && !rawSupported && !winTabSupported) {
                    return "Controller runtime loaded, but the Windows input plugins did not initialise. Restart GameDuck after rebuilding dependencies.";
                }
                return null;
            }
            if (operatingSystem.contains("linux") && !IsPluginSupported(linuxPluginClassName)) {
                return "Controller runtime loaded, but the Linux input plugin did not initialise.";
            }
            if (operatingSystem.contains("mac") && !IsPluginSupported(osxPluginClassName)) {
                return "Controller runtime loaded, but the macOS input plugin did not initialise.";
            }
        } catch (ReflectiveOperationException exception) {
            return "Controller runtime loaded, but plugin status could not be verified.";
        }
        return null;
    }

    private boolean IsPluginSupported(String className) throws ReflectiveOperationException {
        Class<?> pluginClass = Class.forName(className);
        Field supportedField = pluginClass.getDeclaredField("supported");
        supportedField.setAccessible(true);
        return supportedField.getBoolean(null);
    }

    private String SafeControllerName(Object controller, Method getNameMethod) {
        try {
            Object name = Invoke(controller, getNameMethod);
            if (name instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the generic label.
        }
        return "a controller";
    }

    private boolean IsWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private List<Object> AsObjectList(Object array) {
        if (array == null || !array.getClass().isArray()) {
            return List.of();
        }

        int length = java.lang.reflect.Array.getLength(array);
        List<Object> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(java.lang.reflect.Array.get(array, index));
        }
        return values;
    }

    private EnumSet<GBButton> CopyButtons(EnumSet<GBButton> buttons) {
        return buttons == null || buttons.isEmpty()
                ? EnumSet.noneOf(GBButton.class)
                : EnumSet.copyOf(buttons);
    }

    private EnumSet<AppShortcut> CopyShortcuts(EnumSet<AppShortcut> shortcuts) {
        return shortcuts == null || shortcuts.isEmpty()
                ? EnumSet.noneOf(AppShortcut.class)
                : EnumSet.copyOf(shortcuts);
    }

    private ControllerLiveSnapshot CopyLiveSnapshot(ControllerLiveSnapshot snapshot) {
        if (snapshot == null) {
            return ControllerLiveSnapshot.Empty(Optional.empty());
        }
        return new ControllerLiveSnapshot(
                snapshot.activeController(),
                List.copyOf(snapshot.activeInputs()),
                CopyButtons(snapshot.boundButtons()),
                CopyShortcuts(snapshot.pressedShortcuts()));
    }

    private void InvalidateLiveSnapshotLocked() {
        cachedLiveSnapshot = null;
    }
}
