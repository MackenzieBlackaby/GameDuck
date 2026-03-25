package com.blackaby.Backend.Platform;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Host-facing description of one save-state slot.
 *
 * @param slot slot number
 * @param path managed state path
 * @param exists whether the slot currently has a state file
 * @param lastModified last-modified time for the slot file
 */
public record EmulatorStateSlot(int slot, Path path, boolean exists, FileTime lastModified) {
}
