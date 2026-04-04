package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Misc.ControllerBinding;
import com.blackaby.Misc.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerInputServiceTest {

    @AfterEach
    void resetSettings() {
        Settings.Reset();
    }

    @Test
    void liveSnapshotReusesSinglePhysicalPollWithinFreshnessWindow() {
        Settings.Reset();
        long[] now = { 0L };
        AtomicInteger scanCount = new AtomicInteger();
        AtomicInteger pollCount = new AtomicInteger();
        AtomicInteger componentPollCount = new AtomicInteger();

        ControllerInputService.ControllerHandle handle = controllerHandle(
                "controller-a",
                pollCount,
                componentPollCount,
                Map.of("0", 1f));

        ControllerInputService service = new ControllerInputService(
                () -> now[0],
                () -> {
                    scanCount.incrementAndGet();
                    return new ControllerInputService.ScanResult(List.of(handle), null);
                });

        ControllerInputService.ControllerLiveSnapshot firstSnapshot = service.PollLiveSnapshot();
        assertEquals(1, scanCount.get());
        assertEquals(1, pollCount.get());
        assertEquals(1, componentPollCount.get());
        assertEquals(Optional.of(new ControllerInputService.ControllerDevice("controller-a", "controller-a")),
                firstSnapshot.activeController());
        assertEquals(List.of(ControllerBinding.Button("0")), firstSnapshot.activeInputs());
        assertTrue(firstSnapshot.boundButtons().contains(GBButton.A));
        assertFalse(firstSnapshot.pressedShortcuts().iterator().hasNext());

        now[0] = 10L;
        assertTrue(service.PollBoundButtons().contains(GBButton.A));
        assertIterableEquals(List.of(ControllerBinding.Button("0")), service.PollActiveInputs());
        assertEquals(1, pollCount.get());
        assertEquals(1, componentPollCount.get());
    }

    @Test
    void disconnectFallsBackToNextControllerWithoutRescan() {
        Settings.Reset();
        long[] now = { 0L };
        AtomicInteger scanCount = new AtomicInteger();
        AtomicInteger firstPollCount = new AtomicInteger();
        AtomicInteger secondPollCount = new AtomicInteger();

        ControllerInputService.ControllerHandle disconnectedHandle = new ControllerInputService.ControllerHandle(
                new ControllerInputService.ControllerDevice("controller-a", "controller-a"),
                () -> {
                    firstPollCount.incrementAndGet();
                    return false;
                },
                componentValues -> componentValues.put("0", 1f),
                List.of(new ControllerInputService.ComponentHandle("0", ControllerInputService.ComponentKind.BUTTON)));

        ControllerInputService.ControllerHandle fallbackHandle = new ControllerInputService.ControllerHandle(
                new ControllerInputService.ControllerDevice("controller-b", "controller-b"),
                () -> {
                    secondPollCount.incrementAndGet();
                    return true;
                },
                componentValues -> componentValues.put("1", 1f),
                List.of(new ControllerInputService.ComponentHandle("1", ControllerInputService.ComponentKind.BUTTON)));

        ControllerInputService service = new ControllerInputService(
                () -> now[0],
                () -> {
                    scanCount.incrementAndGet();
                    return new ControllerInputService.ScanResult(List.of(disconnectedHandle, fallbackHandle), null);
                });

        ControllerInputService.ControllerPollSnapshot snapshot = service.PollInputSnapshot();

        assertEquals(1, scanCount.get());
        assertEquals(1, firstPollCount.get());
        assertEquals(1, secondPollCount.get());
        assertTrue(snapshot.boundButtons().contains(GBButton.B));
        assertEquals(Optional.of(new ControllerInputService.ControllerDevice("controller-b", "controller-b")),
                service.GetActiveController());
    }

    @Test
    void gameplayPollingDoesNotTriggerSlowRescanButDeviceQueriesDo() {
        Settings.Reset();
        long[] now = { 0L };
        AtomicInteger scanCount = new AtomicInteger();
        AtomicInteger pollCount = new AtomicInteger();

        ControllerInputService.ControllerHandle handle = controllerHandle(
                "controller-a",
                pollCount,
                new AtomicInteger(),
                Map.of("0", 1f));

        ControllerInputService service = new ControllerInputService(
                () -> now[0],
                () -> {
                    scanCount.incrementAndGet();
                    return new ControllerInputService.ScanResult(List.of(handle), null);
                });

        service.PollInputSnapshot();
        assertEquals(1, scanCount.get());

        now[0] = 5000L;
        service.PollInputSnapshot();
        assertEquals(1, scanCount.get());

        service.GetAvailableControllers();
        assertEquals(2, scanCount.get());
        assertTrue(pollCount.get() >= 2);
    }

    private ControllerInputService.ControllerHandle controllerHandle(String deviceId, AtomicInteger pollCount,
            AtomicInteger componentPollCount, Map<String, Float> values) {
        List<ControllerInputService.ComponentHandle> components = values.keySet().stream()
                .sorted()
                .map(componentId -> new ControllerInputService.ComponentHandle(componentId,
                        ControllerInputService.ComponentKind.BUTTON))
                .toList();
        return new ControllerInputService.ControllerHandle(
                new ControllerInputService.ControllerDevice(deviceId, deviceId),
                () -> {
                    pollCount.incrementAndGet();
                    return true;
                },
                componentValues -> {
                    componentPollCount.incrementAndGet();
                    componentValues.putAll(values);
                },
                components);
    }
}
