package com.blackaby.Backend.Emulation.Misc;

import com.blackaby.Backend.Emulation.Memory.CartridgeMapperType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a Game Boy ROM image loaded from disk.
 * <p>
 * The class exposes the raw image bytes together with the cartridge header
 * fields needed for mapper selection and bank sizing.
 */
public class ROM {

    private static final int headerCartridgeType = 0x0147;
    private static final int headerRomSize = 0x0148;
    private static final int headerRamSize = 0x0149;
    private static final int headerCgbFlag = 0x0143;
    private static final int headerTitleStart = 0x0134;
    private static final int headerTitleEnd = 0x0143;

    private final String filename;
    private final String displayName;
    private final List<String> patchNames;
    private final List<String> patchSourcePaths;
    private String headerTitle;
    private int[] data;
    private int cartridgeTypeCode;
    private CartridgeMapperType mapperType;
    private int declaredRomBankCount;
    private int effectiveRomBankCount;
    private int externalRamSizeBytes;
    private boolean batteryBackedSave;
    private boolean cgbCompatible;
    private boolean cgbOnly;

    /**
     * Loads a ROM image from the supplied path.
     *
     * @param filename ROM path
     */
    public ROM(String filename) {
        this(filename, LoadRomBytes(filename), DisplayNameFromPath(filename), List.of(), List.of());
    }

    private ROM(String filename, byte[] romBytes, String displayName, List<String> patchNames, List<String> patchSourcePaths) {
        this.filename = filename;
        this.displayName = displayName == null || displayName.isBlank()
                ? DisplayNameFromPath(filename)
                : displayName;
        this.patchNames = List.copyOf(patchNames == null ? List.of() : patchNames);
        this.patchSourcePaths = List.copyOf(patchSourcePaths == null ? List.of() : patchSourcePaths);
        data = new int[romBytes.length];
        for (int index = 0; index < romBytes.length; index++) {
            data[index] = romBytes[index] & 0xFF;
        }
        ParseHeader();
    }

    /**
     * Loads a ROM from disk and applies an IPS patch to it.
     *
     * @param romFilename base ROM path
     * @param patchFilename IPS patch path
     * @return patched ROM image
     * @throws IOException when either file cannot be read
     */
    public static ROM LoadPatched(String romFilename, String patchFilename) throws IOException {
        return LoadPatched(romFilename, patchFilename, DisplayNameFromPath(patchFilename));
    }

    /**
     * Loads a ROM from disk and applies an IPS patch to it using an explicit
     * display name for the patch layer.
     *
     * @param romFilename base ROM path
     * @param patchFilename IPS patch path
     * @param patchDisplayName patch name shown in the UI and save identity
     * @return patched ROM image
     * @throws IOException when either file cannot be read
     */
    public static ROM LoadPatched(String romFilename, String patchFilename, String patchDisplayName) throws IOException {
        byte[] romBytes = Files.readAllBytes(Path.of(romFilename));
        byte[] patchedBytes = IpsPatch.Apply(romBytes, Path.of(patchFilename));
        String displayName = patchDisplayName == null || patchDisplayName.isBlank()
                ? DisplayNameFromPath(patchFilename)
                : patchDisplayName;
        return new ROM(romFilename, patchedBytes, displayName,
                List.of(displayName), List.of(patchFilename));
    }

    /**
     * Applies an IPS patch to an already loaded ROM image.
     *
     * @param baseRom base ROM image
     * @param patchFilename IPS patch path
     * @return patched ROM image
     * @throws IOException when the patch file cannot be read
     */
    public static ROM LoadPatched(ROM baseRom, String patchFilename) throws IOException {
        return LoadPatched(baseRom, patchFilename, DisplayNameFromPath(patchFilename));
    }

    /**
     * Applies an IPS patch to an already loaded ROM image using an explicit
     * display name for the patch layer.
     *
     * @param baseRom base ROM image
     * @param patchFilename IPS patch path
     * @param patchDisplayName patch name shown in the UI and save identity
     * @return patched ROM image
     * @throws IOException when the patch file cannot be read
     */
    public static ROM LoadPatched(ROM baseRom, String patchFilename, String patchDisplayName) throws IOException {
        if (baseRom == null) {
            throw new IllegalArgumentException("A base ROM is required.");
        }

        byte[] romBytes = baseRom.ToByteArray();
        byte[] patchedBytes = IpsPatch.Apply(romBytes, Path.of(patchFilename));
        List<String> patchNames = new ArrayList<>(baseRom.patchNames);
        List<String> patchSourcePaths = new ArrayList<>(baseRom.patchSourcePaths);
        String displayName = patchDisplayName == null || patchDisplayName.isBlank()
                ? DisplayNameFromPath(patchFilename)
                : patchDisplayName;
        patchNames.add(displayName);
        patchSourcePaths.add(patchFilename);
        return new ROM(baseRom.filename, patchedBytes, displayName, patchNames, patchSourcePaths);
    }

    /**
     * Creates a ROM directly from raw bytes.
     *
     * @param filename source path or identifier
     * @param romBytes ROM bytes
     * @param displayName name to show in the UI
     * @return ROM image instance
     */
    public static ROM FromBytes(String filename, byte[] romBytes, String displayName) {
        return new ROM(filename, Arrays.copyOf(romBytes, romBytes.length), displayName, List.of(), List.of());
    }

    /**
     * Creates a ROM directly from raw bytes while preserving patch identity.
     *
     * @param filename source path or identifier
     * @param romBytes ROM bytes
     * @param displayName name to show in the UI
     * @param patchNames applied patch display names
     * @param patchSourcePaths applied patch source paths
     * @return ROM image instance
     */
    public static ROM FromBytes(String filename, byte[] romBytes, String displayName, List<String> patchNames,
                                List<String> patchSourcePaths) {
        return new ROM(filename, Arrays.copyOf(romBytes, romBytes.length), displayName, patchNames, patchSourcePaths);
    }

    /**
     * Returns the ROM bytes as unsigned values.
     *
     * @return ROM byte array
     */
    public int[] GetData() {
        return data;
    }

    /**
     * Returns the raw cartridge type byte from the ROM header.
     *
     * @return cartridge type code
     */
    public int GetCartridgeTypeCode() {
        return cartridgeTypeCode;
    }

    /**
     * Returns the mapper family derived from the header.
     *
     * @return mapper family
     */
    public CartridgeMapperType GetMapperType() {
        return mapperType;
    }

    /**
     * Returns the bank count declared by the header.
     *
     * @return declared ROM bank count
     */
    public int GetDeclaredRomBankCount() {
        return declaredRomBankCount;
    }

    /**
     * Returns the effective bank count derived from the file length.
     *
     * @return effective ROM bank count
     */
    public int GetEffectiveRomBankCount() {
        return effectiveRomBankCount;
    }

    /**
     * Returns the external RAM size in bytes.
     *
     * @return external RAM size
     */
    public int GetExternalRamSizeBytes() {
        return externalRamSizeBytes;
    }

    /**
     * Returns whether this cartridge uses battery-backed save storage.
     *
     * @return {@code true} when the cartridge should persist save RAM
     */
    public boolean HasBatteryBackedSave() {
        return batteryBackedSave;
    }

    /**
     * Returns whether the cartridge advertises CGB compatibility.
     *
     * @return {@code true} when the header marks the ROM as Game Boy Color capable
     */
    public boolean IsCgbCompatible() {
        return cgbCompatible;
    }

    /**
     * Returns whether the cartridge can boot on both GB and GBC hardware while
     * exposing GBC-specific enhancements when available.
     *
     * @return {@code true} when the ROM is dual-mode rather than GBC-only
     */
    public boolean IsCgbEnhanced() {
        return cgbCompatible && !cgbOnly;
    }

    /**
     * Returns whether the cartridge requires Game Boy Color hardware.
     *
     * @return {@code true} when the ROM cannot boot in DMG mode
     */
    public boolean IsCgbOnly() {
        return cgbOnly;
    }

    /**
     * Returns the original ROM source path used to create this cartridge image.
     *
     * @return source ROM path
     */
    public String GetSourcePath() {
        return filename;
    }

    /**
     * Returns the ROM display name without the file extension.
     *
     * @return ROM display name
     */
    public String GetName() {
        return displayName;
    }

    /**
     * Returns the applied IPS patch names in application order.
     *
     * @return patch display names
     */
    public List<String> GetPatchNames() {
        return patchNames;
    }

    /**
     * Returns the applied IPS patch source paths in application order.
     *
     * @return patch source paths
     */
    public List<String> GetPatchSourcePaths() {
        return patchSourcePaths;
    }

    /**
     * Returns the underlying ROM filename without the file extension.
     *
     * @return source ROM name
     */
    public String GetSourceName() {
        return DisplayNameFromPath(filename);
    }

    /**
     * Returns the title stored in the cartridge header when present.
     *
     * @return header title
     */
    public String GetHeaderTitle() {
        return headerTitle;
    }

    /**
     * Returns a byte-for-byte copy of the ROM image.
     *
     * @return ROM bytes
     */
    public byte[] ToByteArray() {
        byte[] romBytes = new byte[data.length];
        for (int index = 0; index < data.length; index++) {
            romBytes[index] = (byte) (data[index] & 0xFF);
        }
        return romBytes;
    }

    private void ParseHeader() {
        cartridgeTypeCode = HeaderByte(headerCartridgeType);
        mapperType = DecodeMapperType(cartridgeTypeCode);
        declaredRomBankCount = DecodeRomBankCount(HeaderByte(headerRomSize));
        effectiveRomBankCount = Math.max(1, (data.length + 0x3FFF) / 0x4000);
        headerTitle = ExtractHeaderTitle();
        batteryBackedSave = DecodeBatteryBackedSave(cartridgeTypeCode);
        int cgbFlag = HeaderByte(headerCgbFlag);
        cgbCompatible = DecodeCgbCompatible(cgbFlag);
        cgbOnly = DecodeCgbOnly(cgbFlag);
        externalRamSizeBytes = switch (mapperType) {
            case MBC2 -> 0x200;
            default -> DecodeRamSize(HeaderByte(headerRamSize));
        };
    }

    private int HeaderByte(int address) {
        if (data == null || address < 0 || address >= data.length) {
            return 0;
        }
        return data[address] & 0xFF;
    }

    private String ExtractHeaderTitle() {
        if (data == null || data.length <= headerTitleStart) {
            return "";
        }

        int limit = Math.min(headerTitleEnd, data.length - 1);
        byte[] titleBytes = new byte[Math.max(0, limit - headerTitleStart + 1)];
        int length = 0;
        for (int address = headerTitleStart; address <= limit; address++) {
            int value = data[address] & 0xFF;
            if (value == 0x00) {
                break;
            }
            if (value < 0x20 || value > 0x7E) {
                continue;
            }
            titleBytes[length++] = (byte) value;
        }

        if (length == 0) {
            return "";
        }

        return new String(titleBytes, 0, length, StandardCharsets.US_ASCII).trim();
    }

    private static CartridgeMapperType DecodeMapperType(int cartridgeTypeCode) {
        return switch (cartridgeTypeCode) {
            case 0x00, 0x08, 0x09 -> CartridgeMapperType.ROM_ONLY;
            case 0x01, 0x02, 0x03 -> CartridgeMapperType.MBC1;
            case 0x05, 0x06 -> CartridgeMapperType.MBC2;
            case 0x0F, 0x10, 0x11, 0x12, 0x13 -> CartridgeMapperType.MBC3;
            case 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E -> CartridgeMapperType.MBC5;
            default -> CartridgeMapperType.UNSUPPORTED;
        };
    }

    private static int DecodeRomBankCount(int romSizeCode) {
        return switch (romSizeCode) {
            case 0x00 -> 2;
            case 0x01 -> 4;
            case 0x02 -> 8;
            case 0x03 -> 16;
            case 0x04 -> 32;
            case 0x05 -> 64;
            case 0x06 -> 128;
            case 0x07 -> 256;
            case 0x08 -> 512;
            case 0x52 -> 72;
            case 0x53 -> 80;
            case 0x54 -> 96;
            default -> 2;
        };
    }

    private static boolean DecodeBatteryBackedSave(int cartridgeTypeCode) {
        return switch (cartridgeTypeCode) {
            case 0x03, 0x06, 0x09, 0x0F, 0x10, 0x13, 0x1B, 0x1E -> true;
            default -> false;
        };
    }

    private static boolean DecodeCgbCompatible(int cgbFlag) {
        return cgbFlag == 0x80 || cgbFlag == 0xC0;
    }

    private static boolean DecodeCgbOnly(int cgbFlag) {
        return cgbFlag == 0xC0;
    }

    private static int DecodeRamSize(int ramSizeCode) {
        return switch (ramSizeCode) {
            case 0x00 -> 0;
            case 0x01 -> 0x800;
            case 0x02 -> 0x2000;
            case 0x03 -> 0x8000;
            case 0x04 -> 0x20000;
            case 0x05 -> 0x10000;
            default -> 0;
        };
    }

    private static byte[] LoadRomBytes(String filename) {
        if (filename == null || filename.isEmpty()) {
            return new byte[0];
        }

        try {
            return Files.readAllBytes(Path.of(filename));
        } catch (IOException exception) {
            exception.printStackTrace();
            return new byte[0];
        }
    }

    private static String DisplayNameFromPath(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        File file = new File(filename);
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }

    @Deprecated public int[] getData() { return GetData(); }
    @Deprecated public int getCartridgeTypeCode() { return GetCartridgeTypeCode(); }
    @Deprecated public CartridgeMapperType getMapperType() { return GetMapperType(); }
    @Deprecated public int getDeclaredRomBankCount() { return GetDeclaredRomBankCount(); }
    @Deprecated public int getEffectiveRomBankCount() { return GetEffectiveRomBankCount(); }
    @Deprecated public int getExternalRamSizeBytes() { return GetExternalRamSizeBytes(); }
    @Deprecated public boolean hasBatteryBackedSave() { return HasBatteryBackedSave(); }
    @Deprecated public boolean isCgbCompatible() { return IsCgbCompatible(); }
    @Deprecated public boolean isCgbEnhanced() { return IsCgbEnhanced(); }
    @Deprecated public boolean isCgbOnly() { return IsCgbOnly(); }
    @Deprecated public String getSourcePath() { return GetSourcePath(); }
    @Deprecated public String getName() { return GetName(); }
    @Deprecated public List<String> getPatchNames() { return GetPatchNames(); }
    @Deprecated public List<String> getPatchSourcePaths() { return GetPatchSourcePaths(); }
    @Deprecated public String getSourceName() { return GetSourceName(); }
    @Deprecated public String getHeaderTitle() { return GetHeaderTitle(); }
    @Deprecated public byte[] toByteArray() { return ToByteArray(); }
}
