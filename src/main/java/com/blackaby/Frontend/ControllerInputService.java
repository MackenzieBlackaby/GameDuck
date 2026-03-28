package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Misc.ControllerBinding;
import com.blackaby.Misc.Settings;
import com.github.strikerx3.jxinput.XInputAxes;
import com.github.strikerx3.jxinput.XInputButtons;
import com.github.strikerx3.jxinput.XInputComponents;
import com.github.strikerx3.jxinput.XInputDevice;
import com.github.strikerx3.jxinput.exceptions.XInputNotLoadedException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

    private enum ComponentKind {
        BUTTON,
        AXIS,
        POV
    }

    private enum ControllerBackend {
        JINPUT,
        XINPUT
    }

    private static final ControllerInputService instance = new ControllerInputService();
    private static final long rescanIntervalMillis = 2000L;
    private static final String nativeVersion = "2.0.10";
    private static final String controllerEnvironmentClassName = "net.java.games.input.ControllerEnvironment";
    private static final String directInputPluginClassName = "net.java.games.input.DirectInputEnvironmentPlugin";
    private static final String rawInputPluginClassName = "net.java.games.input.RawInputEnvironmentPlugin";
    private static final String winTabPluginClassName = "net.java.games.input.WinTabEnvironmentPlugin";
    private static final String linuxPluginClassName = "net.java.games.input.LinuxEnvironmentPlugin";
    private static final String osxPluginClassName = "net.java.games.input.OSXEnvironmentPlugin";
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
            new ComponentHandle(null, "0", ComponentKind.BUTTON),
            new ComponentHandle(null, "1", ComponentKind.BUTTON),
            new ComponentHandle(null, "2", ComponentKind.BUTTON),
            new ComponentHandle(null, "3", ComponentKind.BUTTON),
            new ComponentHandle(null, "4", ComponentKind.BUTTON),
            new ComponentHandle(null, "5", ComponentKind.BUTTON),
            new ComponentHandle(null, "6", ComponentKind.BUTTON),
            new ComponentHandle(null, "7", ComponentKind.BUTTON),
            new ComponentHandle(null, "8", ComponentKind.BUTTON),
            new ComponentHandle(null, "9", ComponentKind.BUTTON),
            new ComponentHandle(null, povComponentId, ComponentKind.POV),
            new ComponentHandle(null, "x", ComponentKind.AXIS),
            new ComponentHandle(null, "y", ComponentKind.AXIS),
            new ComponentHandle(null, "rx", ComponentKind.AXIS),
            new ComponentHandle(null, "ry", ComponentKind.AXIS),
            new ComponentHandle(null, "z", ComponentKind.AXIS),
            new ComponentHandle(null, "rz", ComponentKind.AXIS));

    private final Object pollLock = new Object();
    private volatile long lastScanTimestamp;
    private volatile String initialisationError;
    private boolean nativeLibrariesReady;
    private boolean missingRuntimeReported;
    private final Map<String, Float> polledComponentValues = new HashMap<>();
    private List<ControllerHandle> controllerHandles = List.of();
    private ControllerHandle activeController;

    private ControllerInputService() {
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
            EnsureRecentScanLocked();
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
            EnsureRecentScanLocked();
            return activeController == null
                    ? Optional.empty()
                    : Optional.of(activeController.device());
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
            ScanControllersLocked();
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
            EnsureRecentScanLocked();
            if (!Settings.controllerInputEnabled) {
                return EnumSet.noneOf(GBButton.class);
            }
            return PollBoundButtonsLocked();
        }
    }

    /**
     * Polls the active controller and returns the currently active raw bindings.
     *
     * @return active raw inputs
     */
    public List<ControllerBinding> PollActiveInputs() {
        synchronized (pollLock) {
            EnsureRecentScanLocked();
            ControllerHandle handle = PollActiveControllerLocked();
            if (handle == null) {
                return List.of();
            }

            Map<String, Float> componentValues = PollComponentValues(handle, polledComponentValues);
            List<ControllerBinding> activeInputs = new ArrayList<>();
            for (ComponentHandle component : handle.components()) {
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

                float deadzone = Math.max(Settings.controllerDeadzonePercent / 100f, 0.35f);
                if (value >= deadzone) {
                    activeInputs.add(ControllerBinding.Axis(component.id(), true));
                } else if (value <= -deadzone) {
                    activeInputs.add(ControllerBinding.Axis(component.id(), false));
                }
            }

            activeInputs.sort(Comparator.comparing(ControllerBinding::ToDisplayText));
            return activeInputs;
        }
    }

    private EnumSet<GBButton> PollBoundButtonsLocked() {
        ControllerHandle handle = PollActiveControllerLocked();
        if (handle == null) {
            return EnumSet.noneOf(GBButton.class);
        }

        float deadzone = Settings.controllerDeadzonePercent / 100f;
        Map<String, Float> componentValues = PollComponentValues(handle, polledComponentValues);
        EnumSet<GBButton> pressedButtons = EnumSet.noneOf(GBButton.class);
        for (GBButton button : GBButton.values()) {
            ControllerBinding binding = Settings.controllerBindings.GetBinding(button);
            if (binding != null && binding.Matches(componentValues, deadzone)) {
                pressedButtons.add(button);
            }
        }
        return pressedButtons;
    }

    private ControllerHandle PollActiveControllerLocked() {
        ControllerHandle handle = SelectActiveControllerLocked();
        if (handle == null) {
            return null;
        }

        if (PollController(handle)) {
            return handle;
        }

        ScanControllersLocked();
        handle = SelectActiveControllerLocked();
        if (handle == null || !PollController(handle)) {
            return null;
        }
        return handle;
    }

    private void EnsureRecentScanLocked() {
        if ((System.currentTimeMillis() - lastScanTimestamp) >= rescanIntervalMillis) {
            ScanControllersLocked();
        }
    }

    private void ScanControllersLocked() {
        List<ControllerHandle> discoveredControllers = new ArrayList<>();
        List<String> backendErrors = new ArrayList<>();

        DiscoverXInputControllers(discoveredControllers, backendErrors);
        DiscoverJInputControllers(discoveredControllers, backendErrors);

        controllerHandles = List.copyOf(discoveredControllers);
        activeController = null;
        SelectActiveControllerLocked();
        initialisationError = DetermineInitialisationError(backendErrors, discoveredControllers.isEmpty());
        lastScanTimestamp = System.currentTimeMillis();
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
                        discoveredControllers.add(new ControllerHandle(
                                ControllerBackend.XINPUT,
                                device,
                                BuildXInputDevice(device),
                                xInputComponents));
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

    private void DiscoverJInputControllers(List<ControllerHandle> discoveredControllers, List<String> backendErrors) {
        List<String> scanWarnings = new ArrayList<>();
        try {
            EnsureRuntimeAvailable();
            EnsureNativeLibrariesLocked();
            Object environment = ResetEnvironmentCacheAndGetEnvironment();
            for (Object controller : AsObjectList(Invoke(environment, "getControllers"))) {
                TryCollectController(controller, discoveredControllers, scanWarnings);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError exception) {
            missingRuntimeReported = true;
            backendErrors.add("Generic controller runtime not found on the classpath. Reload the project dependencies and restart GameDuck.");
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

        try {
            String controllerTypeName = String.valueOf(Invoke(controller, "getType"));
            List<ComponentHandle> components = IndexComponents(controller, scanWarnings);
            if (ShouldIncludeController(controllerTypeName, components)) {
                discoveredControllers.add(new ControllerHandle(ControllerBackend.JINPUT,
                        controller,
                        BuildDevice(controller),
                        components));
            }
        } catch (RuntimeException exception) {
            scanWarnings.add("Skipped " + SafeControllerName(controller) + " because it could not be inspected.");
        }

        for (Object child : AsObjectList(Invoke(controller, "getControllers"))) {
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

    private ControllerDevice BuildDevice(Object controller) {
        String typeName = String.valueOf(Invoke(controller, "getType"));
        String portType = String.valueOf(Invoke(controller, "getPortType"));
        String portNumber = String.valueOf(Invoke(controller, "getPortNumber"));
        String controllerName = String.valueOf(Invoke(controller, "getName"));
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

    private List<ComponentHandle> IndexComponents(Object controller, List<String> scanWarnings) {
        List<ComponentHandle> components = new ArrayList<>();
        for (Object component : AsObjectList(Invoke(controller, "getComponents"))) {
            try {
                if (component == null || Boolean.TRUE.equals(Invoke(component, "isRelative"))) {
                    continue;
                }

                Object identifier = Invoke(component, "getIdentifier");
                String componentId = String.valueOf(Invoke(identifier, "getName"));
                ComponentKind componentKind = DetermineComponentKind(identifier, componentId);
                components.add(new ComponentHandle(component, componentId, componentKind));
            } catch (RuntimeException exception) {
                if (scanWarnings != null) {
                    scanWarnings.add("Skipped a controller component because it could not be read.");
                }
            }
        }
        return List.copyOf(components);
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
        if (handle == null) {
            return false;
        }

        if (handle.backend() == ControllerBackend.XINPUT && handle.controller() instanceof XInputDevice xInputDevice) {
            return xInputDevice.poll() && xInputDevice.isConnected();
        }

        Object result = Invoke(handle.controller(), "poll");
        return result instanceof Boolean pollResult && pollResult;
    }

    private Map<String, Float> PollComponentValues(ControllerHandle handle, Map<String, Float> componentValues) {
        componentValues.clear();
        if (handle.backend() == ControllerBackend.XINPUT && handle.controller() instanceof XInputDevice xInputDevice) {
            return PollXInputComponentValues(xInputDevice, componentValues);
        }

        for (ComponentHandle component : handle.components()) {
            Object value = Invoke(component.component(), "getPollData");
            if (value instanceof Float floatValue) {
                componentValues.put(component.id(), floatValue);
            }
        }
        return componentValues;
    }

    private Map<String, Float> PollXInputComponentValues(XInputDevice device, Map<String, Float> componentValues) {
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
        return componentValues;
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

    private Object Invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access controller runtime method " + methodName + ".", exception);
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

    private String SafeControllerName(Object controller) {
        try {
            Object name = Invoke(controller, "getName");
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

    private record ControllerHandle(ControllerBackend backend, Object controller, ControllerDevice device,
                                    List<ComponentHandle> components) {
    }

    private record ComponentHandle(Object component, String id, ComponentKind kind) {
    }
}


