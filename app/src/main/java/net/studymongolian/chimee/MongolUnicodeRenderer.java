package net.studymongolian.chimee;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/*
 * Chimee Mongol Unicode Rendering Engine
 * Version 3.0.0
 * 
 * Current version needs to be used with Almas font glyphs
 * copied to PUA starting at \uE360. To use different glyph
 * encodings, adjust the GLYPH_* static final constants below.
 * These PUA encodings are only to be used internally for glyph
 * selection. All external text should use Unicode.
 */
public final class MongolUnicodeRenderer {

    // static final constants are declared at end of class for readability

    // this is a singleton class
    public final static MongolUnicodeRenderer INSTANCE = new MongolUnicodeRenderer();

    // private class variables
    private Map<String, String> mIsolateMap; // <Unicode, glyph>
    private Map<String, String> mInitialMap; // <Unicode, glyph>
    private Map<String, String> mMedialMap; // <Unicode, glyph>
    private Map<String, String> mFinalMap; // <Unicode, glyph>
    private Map<String, String> mSuffixMap; // <Unicode, complete_suffix_glyph_string>

    public enum Location {
        ISOLATE, INITIAL, MEDIAL, FINAL, NOT_MONGOLIAN
    }

    // Constructor
    private MongolUnicodeRenderer() {
        init();
    }

    public String unicodeToGlyphs(String inputString) {
        StringBuilder outputString = new StringBuilder();
        StringBuilder subString = new StringBuilder();

        if (TextUtils.isEmpty(inputString)) {
            return "";
        }

        // Loop through characters in string
        //char[] charArray = inputString.toCharArray();
        boolean isMongolSubString = isMongolian(inputString.charAt(0));
        for (char character : inputString.toCharArray()) {

            if (isMongolian(character) || character == Uni.NNBS || character== CURSOR_HOLDER) {

                if (!isMongolSubString) {
                    outputString.append(subString.toString());
                    subString.setLength(0);
                }

                subString.append(character);
                isMongolSubString = true;

            } else { // non-Mongol character

                if (isMongolSubString) {
                    // break up word from suffixes
                    String[] parts = subString.toString().split(String.valueOf(Uni.NNBS), -1);
                    for (int j = 0; j < parts.length; j++) {
                        if (j == 0) { // this is the main word
                            // Convert mongol word to glyphs and add to output string
                            outputString.append(convertWord(parts[j]));
                        } else { // these are the suffixes
                            // remove the cursor holder character
                            String tempSuffix = parts[j].replace(String.valueOf(CURSOR_HOLDER), "");
                            outputString.append(Uni.NNBS);
                            if (mSuffixMap.containsKey(tempSuffix)) {
                                outputString.append(mSuffixMap.get(tempSuffix));
                                if (parts[j].contains(String.valueOf(CURSOR_HOLDER))) {
                                    outputString.append(CURSOR_HOLDER);
                                }
                            } else {
                                outputString.append(convertWord(parts[j]));
                            }
                        }
                    }

                    // reset substring
                    subString.setLength(0);
                    subString.append(character);

                } else {
                    // Add nonMongol chars to string
                    subString.append(character);
                }
                isMongolSubString = false;
            }
        }

        // Add any final substring
        if (subString.length() > 0) {
            if (isMongolSubString) {
                // TODO This is not DRY code, see above
                // break up word from suffixes
                String[] parts = subString.toString().split(String.valueOf(Uni.NNBS), -1);
                for (int j = 0; j < parts.length; j++) {
                    if (j == 0) { // this is the main word
                        // Convert mongol word to glyphs and add to output string
                        outputString.append(convertWord(parts[j]));
                    } else { // these are the suffixes
                        String tempSuffix = parts[j].replace(String.valueOf(CURSOR_HOLDER), "");
                        outputString.append(Uni.NNBS);
                        if (mSuffixMap.containsKey(tempSuffix)) {
                            outputString.append(mSuffixMap.get(tempSuffix));
                            if (parts[j].contains(String.valueOf(CURSOR_HOLDER))) {
                                outputString.append(CURSOR_HOLDER);
                            }
                        } else {
                            outputString.append(convertWord(parts[j]));
                        }
                    }
                }
            } else {
                // Add nonMongol chars to string
                outputString.append(subString.toString());
            }
        }

        return outputString.toString();
    }

    /**
     * Used to get the unicode character position index from a touch event that gives a glyph
     * position index
     *
     * @param unicodeString This is the string that produced the glyph string param
     * @param glyphIndex
     * @return the Unicode character position
     */
    public int getUnicodeIndex(String unicodeString, int glyphIndex) {

        // TODO This will be slow for long strings
        String glyphString = unicodeToGlyphs(unicodeString);
        // TODO sometimes this displays differently (angli delete "l", touch end)

        // error catching
        if (glyphIndex >= glyphString.length()) {
            return unicodeString.length();
        }

        // Find the matching group between spaces
        int glyphSpaceCount = 0;
        int glyphSpaceIndex = 0;
        for (int i = 0; i < glyphIndex; i++) {
            if (glyphString.charAt(i) == ' ') {
                glyphSpaceCount++;
                glyphSpaceIndex = i;
            }
        }
        String glyphGroup = glyphString.substring(glyphSpaceIndex);
        int unicodeSpaceCount = 0;
        int unicodeSpaceIndex = 0;
        if (glyphSpaceCount > 0) {
            for (int i = 0; i < unicodeString.length(); i++) {
                if (unicodeString.charAt(i) == ' ') {
                    unicodeSpaceCount++;
                    unicodeSpaceIndex = i;
                    if (unicodeSpaceCount == glyphSpaceCount) {
                        break;
                    }
                }
            }
        }

        String unicodeGroup = unicodeString.substring(unicodeSpaceIndex);

        // increment until glyphs match
        int groupGlyphIndex = glyphIndex - glyphSpaceIndex;
        int groupUnicodeIndex = groupGlyphIndex;
        boolean isMedial = false;
        if (groupGlyphIndex > 0 && groupGlyphIndex < glyphGroup.length()) {
            isMedial = isMongolianGlyphAlphabet(glyphGroup.charAt(groupGlyphIndex))
                    && isMongolianGlyphAlphabet(glyphGroup.charAt(groupGlyphIndex - 1));
        }
        for (int i = groupGlyphIndex; i < unicodeGroup.length(); i++) {
            if (!isFVS(unicodeGroup.charAt(i))) {
                if (isMedial) {
                    if (glyphGroup.substring(0, groupGlyphIndex).equals(
                            unicodeToGlyphs(unicodeGroup.substring(0, i) + Uni.ZWJ))) {
                        groupUnicodeIndex = i;
                        break;
                    }
                } else {
                    if (glyphGroup.substring(0, groupGlyphIndex).equals(
                            unicodeToGlyphs(unicodeGroup.substring(0, i)))) {
                        groupUnicodeIndex = i;
                        break;
                    }
                }

            }
        }

        return unicodeSpaceIndex + groupUnicodeIndex;
    }

    private String convertWord(String mongolWord) {

        // Error checking
        if (TextUtils.isEmpty(mongolWord)) {
            return "";
        }

        final int MAXIMUM_SEARCH_LENGTH = 4; // max length in HashMap is 4(?).
        String formattedMongolWord = "";
        StringBuilder returnString = new StringBuilder();

        // Check if cursor holder is present
        boolean startsWithCursorHolder = mongolWord.startsWith(String.valueOf(CURSOR_HOLDER));
        boolean endsWithCursorHolder = mongolWord.endsWith(String.valueOf(CURSOR_HOLDER));
        if (mongolWord.equals(String.valueOf(CURSOR_HOLDER))) {
            return mongolWord;
        } else if (startsWithCursorHolder) {
            formattedMongolWord = mongolWord.substring(1);
        } else if (endsWithCursorHolder) {
            formattedMongolWord = mongolWord.substring(0, mongolWord.length() - 1);
        } else {
            formattedMongolWord = mongolWord;
        }

        // apply rules
        formattedMongolWord = preFormatter(formattedMongolWord);

        // Check whole word in isolate table
        if (formattedMongolWord.length() <= MAXIMUM_SEARCH_LENGTH
                && mIsolateMap.containsKey(formattedMongolWord)) {

            returnString.append(String.valueOf(mIsolateMap.get(formattedMongolWord)));

            if (startsWithCursorHolder) {
                return String.valueOf(CURSOR_HOLDER) + returnString.toString();
            } else if (endsWithCursorHolder) {
                return returnString.toString() + String.valueOf(CURSOR_HOLDER);
            } else {
                return returnString.toString();
            }
        }

        // initialize variables
        int initialEndIndex = 0;
        int finalStartIndex = 0;
        int medialStartIndex = 0;
        int medialEndIndex = 0;

        // Find longest matching initial (search long to short) TODO is this slow?
        String subString = "";
        String match = "";
        int start;
        if (formattedMongolWord.length() > MAXIMUM_SEARCH_LENGTH) {
            start = MAXIMUM_SEARCH_LENGTH;
        } else {
            start = formattedMongolWord.length() - 1;
        }
        for (int i = start; i > 0; i--) {
            subString = formattedMongolWord.substring(0, i);
            if (mInitialMap.containsKey(subString)) {
                match = mInitialMap.get(subString);
                initialEndIndex = i;
                break;
            }
        }
        if (startsWithCursorHolder) {
            returnString.append(CURSOR_HOLDER);
        }
        returnString.append(match);

        // Find longest matching final (search long to short) TODO is this slow?
        String finalGlyph = "";
        if (formattedMongolWord.length() > MAXIMUM_SEARCH_LENGTH + initialEndIndex) {
            start = formattedMongolWord.length() - MAXIMUM_SEARCH_LENGTH;
        } else {
            start = initialEndIndex;
        }
        for (int i = start; i < formattedMongolWord.length(); i++) {
            subString = formattedMongolWord.substring(i);
            if (mFinalMap.containsKey(subString)) {
                finalGlyph = mFinalMap.get(subString);
                finalStartIndex = i;
                break;
            }
        }

        // Find string of medials (search long to short) TODO is this slow?
        match = "";
        medialStartIndex = initialEndIndex;
        medialEndIndex = finalStartIndex; // substring endindex is exclusive
        boolean matchFound = false;
        while (medialStartIndex < finalStartIndex) {

            if (medialStartIndex + MAXIMUM_SEARCH_LENGTH < medialEndIndex) {
                start = medialStartIndex + MAXIMUM_SEARCH_LENGTH;
            } else {
                start = medialEndIndex;
            }
            for (int i = start; i > medialStartIndex; i--) {
                subString = formattedMongolWord.substring(medialStartIndex, i);
                if (mMedialMap.containsKey(subString)) {
                    match = mMedialMap.get(subString);
                    // returnString.append(mMedialMap.get(subString));
                    medialStartIndex = i;
                    matchFound = true;
                    break;
                }
            }
            if (!matchFound) {
                break;
            }
            returnString.append(match);
            // medialStartIndex = medialEndIndex;
            match = "";
        }

        // Return [ini + med + fin]
        returnString.append(finalGlyph);
        if (endsWithCursorHolder) {
            returnString.append(CURSOR_HOLDER);
        }
        return returnString.toString();
    }

    // Formatting rules

    private String preFormatter(String mongolWord) {
        // This method applies context based formatting rules by adding the appropriate FVS character
        // TODO This method is slow because every rule has to loop through the word. However, this was intentional in order to separate the rules for easier debugging

        StringBuilder word = new StringBuilder();
        word.append(mongolWord);

        // MVS rule (only formats A/E after the MVS)
        // Consonant before is formatted by lookup table
        // If A/E is not final then ignore MVS (mingg-a -> minggan)
        for (int i = word.length() - 2; i >= 0; i--) {
            if (word.charAt(i) == Uni.MVS) {
                // following char is a vowel
                if (i == word.length() - 2
                        && (word.charAt(i + 1) == Uni.A || word.charAt(i + 1) == Uni.E)) {
                    // insert FVS2 (this is the lower form of FVS1)
                    word.insert(i + 2, Uni.FVS2);
                } else if (i == word.length() - 2 && (word.charAt(i + 1) == Uni.ZWJ)) {
                    // This will still allow consonant to display correctly
                } else { // following letter is not final A/E or ZWJ
                    // ignore MVS
                    word.deleteCharAt(i);
                }
            }
        }

        // Only allow the NG/B/P/F/K/KH and G/Q ligature if A/O/U or MVS follows
        for (int i = word.length() - 3; i >= 0; i--) {
            // this char is NG/B/P/F/K/KH
            if (word.charAt(i) == Uni.ANG || word.charAt(i) == Uni.BA || word.charAt(i) == Uni.PA || word.charAt(i) == Uni.FA || word.charAt(i) == Uni.KA || word.charAt(i) == Uni.KHA) {
                // following char is Q/G
                if (word.charAt(i + 1) == Uni.QA || word.charAt(i + 1) == Uni.GA) {
                    // following char is not A/O/U or MVS (MVS allows NG+G/Q ligature)
                    if (!isMasculineVowel(word.charAt(i + 2)) && word.charAt(i + 2) != Uni.MVS) {
                        // insert ZWJ to prevent ligature between NG/B/P/F/K/KH and G/Q
                        word.insert(i + 1, Uni.ZWJ);
                    }
                }
            }
        }

        // *** OE/UE long tooth in first syllable for non ligatures rule ***
        // (long tooth ligatures are handled by the hash tables)
        if (word.length() > 2) {
            // second char is OE or UE
            if (word.charAt(1) == Uni.OE || word.charAt(1) == Uni.UE) {
                // first char not a vowel or ligature consonant (B/P/Q/G/F/K/KH)
                if (!isVowel(word.charAt(0)) && word.charAt(0) != Uni.BA && word.charAt(0) != Uni.PA && word.charAt(0) != Uni.QA && word.charAt(0) != Uni.GA && word.charAt(0) != Uni.FA && word.charAt(0) != Uni.KA && word.charAt(0) != Uni.KHA) {
                    if (!isFVS(word.charAt(2))) {
                        // insert FVS1 after OE/UE
                        word.insert(2, Uni.FVS1);
                    }
                }
                // second char is FVS and third char is OE or UE
            } else if (isFVS(word.charAt(1)) && word.length() > 3 && (word.charAt(2) == Uni.OE || word.charAt(2) == Uni.UE)) {
                // first char not a vowel or ligature consonant (B/P/Q/G/F/K/KH)
                if (!isVowel(word.charAt(0)) && word.charAt(0) != Uni.BA && word.charAt(0) != Uni.PA && word.charAt(0) != Uni.QA && word.charAt(0) != Uni.GA && word.charAt(0) != Uni.FA && word.charAt(0) != Uni.KA && word.charAt(0) != Uni.KHA) {
                    if (!isFVS(word.charAt(3))) {
                        // insert FVS1 after OE/UE
                        word.insert(3, Uni.FVS1);
                    }
                }
            }
        }

        // *** medial N rule ***
        for (int i = word.length() - 2; i > 0; i--) {
            if (word.charAt(i) == Uni.NA) {
                // following char is a vowel
                if (isVowel(word.charAt(i + 1))) {
                    // insert FVS1
                    word.insert(i + 1, Uni.FVS1);
                }
            }
        }

        // *** medial D rule ***
        for (int i = word.length() - 2; i > 0; i--) {
            if (word.charAt(i) == Uni.DA) {
                // following char is a vowel
                if (isVowel(word.charAt(i + 1))) {
                    // insert FVS1
                    word.insert(i + 1, Uni.FVS1);
                }
            }
        }

        // GA rules
        if (word.charAt(0) == Uni.GA) {

            // Initial GA
            if (word.length() > 1 && isConsonant(word.charAt(1))) {
                // *** Initial GA before consonant rule ***
                // make it a feminine initial GA
                word.insert(1, Uni.FVS2);
            }
        }
        for (int i = word.length() - 1; i > 0; i--) {
            if (word.charAt(i) == Uni.GA) {

                // final GA
                boolean isMasculineWord = false;
                if (i == word.length() - 1) {

                    // **** feminine final GA rule ****
                    for (int j = i - 1; j >= 0; j--) {
                        // vowel I also defaults to feminine
                        if (isMasculineVowel(word.charAt(j))) {
                            isMasculineWord = true;
                            break;
                        }
                    }
                    if (!isMasculineWord) {
                        // make it a feminine final GA
                        word.insert(i + 1, Uni.FVS2);
                    }

                } else { // medial GA

                    // **** dotted medial masculine GA rule ****
                    if (isMasculineVowel(word.charAt(i + 1))) {
                        // add the dots
                        word.insert(i + 1, Uni.FVS1);

                        // **** feminine medial GA rule ****
                    } else if (isConsonant(word.charAt(i + 1))) {
                        boolean isFeminineWord = false;
                        isMasculineWord = false;


                        if (isConsonant(word.charAt(i - 1)) || word.charAt(i - 1) == Uni.ZWJ) {
                            // This means we have consonant+GA+consonant (ex. ANGGLI)
                            // Although the whole word may not actually be feminine, still use the feminine medial GA
                            isFeminineWord = true;
                        }else{
                            // check before GA for gender of vowel
                            for (int j = i - 1; j >= 0; j--) {
                                if (isFeminineVowel(word.charAt(j))) {
                                    isFeminineWord = true;
                                    break;
                                } else if (isMasculineVowel(word.charAt(j))) {
                                    isMasculineWord = true;
                                    break;
                                }
                            }
                        }



                        if (isFeminineWord) {
                            // make it a feminine medial GA
                            word.insert(i + 1, Uni.FVS3);
                        } else if (!isMasculineWord) {

                            // couldn't be determined by looking before
                            // so check after GA for no masculine vowel
                            isMasculineWord = false;
                            for (int j = i + 1; j < word.length(); j++) {
                                // vowel I also defaults to feminine
                                if (isMasculineVowel(word.charAt(j))) {
                                    isMasculineWord = true;
                                    break;
                                }
                            }
                            if (!isMasculineWord) {
                                // make it a feminine medial GA, Thus, I defaults to feminine GA
                                word.insert(i + 1, Uni.FVS3);
                            }
                        }
                    }
                }

            }
        } // End of GA rules

        // *** medial Y rule ***
        // upturn the Y before any vowel except I (when YI follows vowel)
        for (int i = word.length() - 2; i > 0; i--) {
            if (word.charAt(i) == Uni.YA) {
                char nextChar = word.charAt(i + 1);
                char prevChar = word.charAt(i - 1);
                // following char is a vowel besides I (or previous char is consonant)
                if ((isVowel(nextChar) && nextChar != Uni.I) || (!isVowel(prevChar)) && !isFVS(nextChar) && nextChar != Uni.MVS) {
                    // insert FVS1 (hooked Y)
                    word.insert(i + 1, Uni.FVS1);
                }
            }
        }

        // *** medial W rule ***
        // Use the hooked W before any vowel
        for (int i = word.length() - 2; i > 0; i--) {
            if (word.charAt(i) == Uni.WA) {
                if (isVowel(word.charAt(i + 1))) {
                    // insert FVS1 (hooked W)
                    word.insert(i + 1, Uni.FVS1);
                }
            }
        }

        // *** AI, EI, OI, UI, OEI, UEI medial double tooth I diphthong rule ***
        // (this rule should come after OE/UE long tooth in first syllable rule)
        for (int i = word.length() - 2; i > 0; i--) {
            if (word.charAt(i) == Uni.I) {
                // previous char is non I vowel and next char is not FVS
                if (isVowel(word.charAt(i - 1)) && word.charAt(i - 1) != Uni.I
                        && !isFVS(word.charAt(i + 1))) {
                    // insert FVS3 (double tooth medial I)
                    word.insert(i + 1, Uni.FVS3);
                }
            }
        }

        return word.toString();
    }

    public static boolean isVowel(char character) {
        return (character >= Uni.A && character <= Uni.EE);
    }

    private boolean isMasculineVowel(char character) {
        return (character == Uni.A || character == Uni.O || character == Uni.U);
    }

    private boolean isFeminineVowel(char character) {
        return (character == Uni.E || character == Uni.EE || character == Uni.OE || character == Uni.UE);
    }

    public boolean isConsonant(char character) {
        return (character >= Uni.NA && character <= Uni.CHI);
    }

    private boolean isFVS(char character) {
        return (character >= Uni.FVS1 && character <= Uni.FVS3);
    }

    public static boolean isMongolian(char character) {
        // Mongolian letters, MVS, FVS1-3, NIRUGU, Uni.ZWJ, (but not NNBS)
        return ((character >= Uni.A && character <= Uni.CHI)
                || (character >= Uni.MONGOLIAN_NIRUGU && character <= Uni.MVS) || character == Uni.ZWJ);
    }

    public boolean isBGDRS(char character) {
        // This method is not used internally, only for external use.
        return (character == Uni.BA || character == Uni.GA || character == Uni.DA
                || character == Uni.RA || character == Uni.SA);
    }

    public static boolean isMongolianAlphabet(char character) {
        // This method is not used internally, only for external use.
        return (character >= Uni.A && character <= Uni.CHI);
    }

    private boolean isMongolianGlyphAlphabet(char character) {
        return (character >= Glyph.ISOL_A && character <= Glyph.INIT_KHA_MEDI_UE_FVS1);
    }

    public boolean isMasculineWord(String word) {
        // This method is not used internally, only for external use.
        if (word == null || word.equals("")) {
            return false;
        }
        char[] characters = word.toCharArray();
        for (int i = characters.length - 1; i >= 0; i--) {
            if (characters[i] == Uni.A || characters[i] == Uni.O || characters[i] == Uni.U) {
                return true;
            }
        }
        return false;
    }

    public boolean isFeminineWord(String word) {
        // This method is not used internally, only for external use.
        if (word == null || word.equals("")) {
            return false;
        }
        char[] characters = word.toCharArray();
        for (int i = characters.length - 1; i >= 0; i--) {
            if (characters[i] == Uni.E || characters[i] == Uni.OE || characters[i] == Uni.UE
                    || characters[i] == Uni.EE) {
                return true;
            }
        }
        return false;
    }

    public String getIsolate(String lookup) {
        if (mIsolateMap.containsKey(lookup)) {
            return mIsolateMap.get(lookup);
        } else {
            return "";
        }
    }

    public String getInitial(String lookup) {
        if (mInitialMap.containsKey(lookup)) {
            return mInitialMap.get(lookup);
        } else {
            return "";
        }
    }

    public String getMedial(String lookup) {
        if (mMedialMap.containsKey(lookup)) {
            return mMedialMap.get(lookup);
        } else {
            return "";
        }
    }

    public String getFinal(String lookup) {
        if (mFinalMap.containsKey(lookup)) {
            return mFinalMap.get(lookup);
        } else {
            return "";
        }
    }

    private void init() {

        // This is a lot of initialization. Possibly slow?
        initIsolated();
        initInitial();
        initMedial();
        initFinal();
        initSuffixes();

    }

    private void initIsolated() {

        // NOTE: assuming MAXIMUM_SEARCH_LENGTH = 4

        mIsolateMap = new HashMap<String, String>();

        // Single letters
        mIsolateMap.put("" + Uni.A, "" + Glyph.ISOL_A);
        mIsolateMap.put("" + Uni.A + Uni.FVS1, "" + Glyph.ISOL_A_FVS1);
        mIsolateMap.put("" + Uni.E, "" + Glyph.ISOL_E);
        mIsolateMap.put("" + Uni.E + Uni.FVS1, "" + Glyph.ISOL_E_FVS1);
        mIsolateMap.put("" + Uni.I, "" + Glyph.ISOL_I);
        mIsolateMap.put("" + Uni.I + Uni.FVS1, "" + Glyph.ISOL_I_FVS1);
        mIsolateMap.put("" + Uni.O, "" + Glyph.ISOL_O);
        mIsolateMap.put("" + Uni.O + Uni.FVS1, "" + Glyph.ISOL_O_FVS1);
        mIsolateMap.put("" + Uni.U, "" + Glyph.ISOL_U);
        mIsolateMap.put("" + Uni.U + Uni.FVS1, "" + Glyph.ISOL_U_FVS1);
        mIsolateMap.put("" + Uni.U + Uni.FVS2, "" + Glyph.ISOL_U_FVS2);  // I am adding this myself
        mIsolateMap.put("" + Uni.OE, "" + Glyph.ISOL_OE);
        mIsolateMap.put("" + Uni.OE + Uni.FVS1, "" + Glyph.ISOL_OE_FVS1);
        mIsolateMap.put("" + Uni.UE, "" + Glyph.ISOL_UE);
        mIsolateMap.put("" + Uni.UE + Uni.FVS1, "" + Glyph.ISOL_UE_FVS1);
        mIsolateMap.put("" + Uni.UE + Uni.FVS2, "" + Glyph.ISOL_UE_FVS2);
        mIsolateMap.put("" + Uni.UE + Uni.FVS3, "" + Glyph.ISOL_UE_FVS3);  // I am adding this myself
        mIsolateMap.put("" + Uni.EE, "" + Glyph.ISOL_EE);
        mIsolateMap.put("" + Uni.EE + Uni.FVS1, "" + Glyph.ISOL_EE_FVS1);
        mIsolateMap.put("" + Uni.NA, "" + Glyph.ISOL_NA);
        mIsolateMap.put("" + Uni.NA + Uni.FVS1, "" + Glyph.ISOL_NA_FVS1);
        mIsolateMap.put("" + Uni.ANG, "" + Glyph.ISOL_ANG);
        mIsolateMap.put("" + Uni.BA, "" + Glyph.ISOL_BA);
        mIsolateMap.put("" + Uni.PA, "" + Glyph.ISOL_PA);
        mIsolateMap.put("" + Uni.QA, "" + Glyph.ISOL_QA);
        mIsolateMap.put("" + Uni.QA + Uni.FVS1, "" + Glyph.ISOL_QA_FVS1);
        mIsolateMap.put("" + Uni.QA + Uni.FVS2, "" + Glyph.ISOL_QA_FVS2);
        mIsolateMap.put("" + Uni.QA + Uni.FVS3, "" + Glyph.ISOL_QA_FVS3);
        mIsolateMap.put("" + Uni.GA, "" + Glyph.ISOL_GA);
        mIsolateMap.put("" + Uni.GA + Uni.FVS1, "" + Glyph.ISOL_GA_FVS1);
        mIsolateMap.put("" + Uni.GA + Uni.FVS2, "" + Glyph.ISOL_GA_FVS2);
        mIsolateMap.put("" + Uni.GA + Uni.FVS3, "" + Glyph.ISOL_GA_FVS3);
        mIsolateMap.put("" + Uni.MA, "" + Glyph.ISOL_MA);
        mIsolateMap.put("" + Uni.LA, "" + Glyph.ISOL_LA);
        mIsolateMap.put("" + Uni.SA, "" + Glyph.ISOL_SA);
        mIsolateMap.put("" + Uni.SHA, "" + Glyph.ISOL_SHA);
        mIsolateMap.put("" + Uni.TA, "" + Glyph.ISOL_TA);
        mIsolateMap.put("" + Uni.TA + Uni.FVS1, "" + Glyph.ISOL_TA_FVS1);
        mIsolateMap.put("" + Uni.DA, "" + Glyph.ISOL_DA);
        mIsolateMap.put("" + Uni.DA + Uni.FVS1, "" + Glyph.ISOL_DA); // adding this so that partial word with initial D+FVS1 won't switch form
        mIsolateMap.put("" + Uni.CHA, "" + Glyph.ISOL_CHA);
        mIsolateMap.put("" + Uni.JA, "" + Glyph.ISOL_JA);
        mIsolateMap.put("" + Uni.JA + Uni.FVS1, "" + Glyph.ISOL_JA_FVS1);
        mIsolateMap.put("" + Uni.YA, "" + Glyph.ISOL_YA);
        mIsolateMap.put("" + Uni.RA, "" + Glyph.ISOL_RA);
        mIsolateMap.put("" + Uni.WA, "" + Glyph.ISOL_WA);
        mIsolateMap.put("" + Uni.FA, "" + Glyph.ISOL_FA);
        mIsolateMap.put("" + Uni.KA, "" + Glyph.ISOL_KA);
        mIsolateMap.put("" + Uni.KHA, "" + Glyph.ISOL_KHA);
        mIsolateMap.put("" + Uni.TSA, "" + Glyph.ISOL_TSA);
        mIsolateMap.put("" + Uni.ZA, "" + Glyph.ISOL_ZA);
        mIsolateMap.put("" + Uni.HAA, "" + Glyph.ISOL_HAA);
        mIsolateMap.put("" + Uni.ZRA, "" + Glyph.ISOL_ZRA);
        mIsolateMap.put("" + Uni.LHA, "" + Glyph.ISOL_LHA);
        mIsolateMap.put("" + Uni.ZHI, "" + Glyph.ISOL_ZHI);
        mIsolateMap.put("" + Uni.CHI, "" + Glyph.ISOL_CHI);

        // Double letters
        mIsolateMap.put("" + Uni.BA + Uni.A, "" + Glyph.INIT_BA_FINA_A);
        mIsolateMap.put("" + Uni.BA + Uni.E, "" + Glyph.INIT_BA_FINA_E);
        mIsolateMap.put("" + Uni.BA + Uni.I, "" + Glyph.INIT_BA_FINA_I);
        mIsolateMap.put("" + Uni.BA + Uni.O, "" + Glyph.INIT_BA_FINA_O);
        mIsolateMap.put("" + Uni.BA + Uni.U, "" + Glyph.INIT_BA_FINA_U);
        mIsolateMap.put("" + Uni.BA + Uni.OE, "" + Glyph.INIT_BA_FINA_OE);
        mIsolateMap.put("" + Uni.BA + Uni.UE, "" + Glyph.INIT_BA_FINA_UE);
        mIsolateMap.put("" + Uni.BA + Uni.EE, "" + Glyph.INIT_BA_FINA_EE);
        mIsolateMap.put("" + Uni.PA + Uni.A, "" + Glyph.INIT_PA_FINA_A);
        mIsolateMap.put("" + Uni.PA + Uni.E, "" + Glyph.INIT_PA_FINA_E);
        mIsolateMap.put("" + Uni.PA + Uni.I, "" + Glyph.INIT_PA_FINA_I);
        mIsolateMap.put("" + Uni.PA + Uni.O, "" + Glyph.INIT_PA_FINA_O);
        mIsolateMap.put("" + Uni.PA + Uni.U, "" + Glyph.INIT_PA_FINA_U);
        mIsolateMap.put("" + Uni.PA + Uni.OE, "" + Glyph.INIT_PA_FINA_OE);
        mIsolateMap.put("" + Uni.PA + Uni.UE, "" + Glyph.INIT_PA_FINA_UE);
        mIsolateMap.put("" + Uni.PA + Uni.EE, "" + Glyph.INIT_PA_FINA_EE);
        mIsolateMap.put("" + Uni.QA + Uni.E, "" + Glyph.INIT_QA_FINA_E);
        mIsolateMap.put("" + Uni.QA + Uni.I, "" + Glyph.INIT_QA_FINA_I);
        mIsolateMap.put("" + Uni.QA + Uni.OE, "" + Glyph.INIT_QA_FINA_OE);
        mIsolateMap.put("" + Uni.QA + Uni.UE, "" + Glyph.INIT_QA_FINA_UE);
        mIsolateMap.put("" + Uni.QA + Uni.EE, "" + Glyph.INIT_QA_FINA_EE);
        mIsolateMap.put("" + Uni.GA + Uni.E, "" + Glyph.INIT_GA_FINA_E);
        mIsolateMap.put("" + Uni.GA + Uni.I, "" + Glyph.INIT_GA_FINA_I);
        mIsolateMap.put("" + Uni.GA + Uni.OE, "" + Glyph.INIT_GA_FINA_OE);
        mIsolateMap.put("" + Uni.GA + Uni.UE, "" + Glyph.INIT_GA_FINA_UE);
        mIsolateMap.put("" + Uni.GA + Uni.EE, "" + Glyph.INIT_GA_FINA_EE);
        mIsolateMap.put("" + Uni.FA + Uni.A, "" + Glyph.INIT_FA_FINA_A);
        mIsolateMap.put("" + Uni.FA + Uni.E, "" + Glyph.INIT_FA_FINA_E);
        mIsolateMap.put("" + Uni.FA + Uni.I, "" + Glyph.INIT_FA_FINA_I);
        mIsolateMap.put("" + Uni.FA + Uni.O, "" + Glyph.INIT_FA_FINA_O);
        mIsolateMap.put("" + Uni.FA + Uni.U, "" + Glyph.INIT_FA_FINA_U);
        mIsolateMap.put("" + Uni.FA + Uni.OE, "" + Glyph.INIT_FA_FINA_OE);
        mIsolateMap.put("" + Uni.FA + Uni.UE, "" + Glyph.INIT_FA_FINA_UE);
        mIsolateMap.put("" + Uni.FA + Uni.EE, "" + Glyph.INIT_FA_FINA_EE);
        mIsolateMap.put("" + Uni.KA + Uni.A, "" + Glyph.INIT_KA_FINA_A);
        mIsolateMap.put("" + Uni.KA + Uni.E, "" + Glyph.INIT_KA_FINA_E);
        mIsolateMap.put("" + Uni.KA + Uni.I, "" + Glyph.INIT_KA_FINA_I);
        mIsolateMap.put("" + Uni.KA + Uni.O, "" + Glyph.INIT_KA_FINA_O);
        mIsolateMap.put("" + Uni.KA + Uni.U, "" + Glyph.INIT_KA_FINA_U);
        mIsolateMap.put("" + Uni.KA + Uni.OE, "" + Glyph.INIT_KA_FINA_OE);
        mIsolateMap.put("" + Uni.KA + Uni.UE, "" + Glyph.INIT_KA_FINA_UE);
        mIsolateMap.put("" + Uni.KA + Uni.EE, "" + Glyph.INIT_KA_FINA_EE);
        mIsolateMap.put("" + Uni.KHA + Uni.A, "" + Glyph.INIT_KHA_FINA_A);
        mIsolateMap.put("" + Uni.KHA + Uni.E, "" + Glyph.INIT_KHA_FINA_E);
        mIsolateMap.put("" + Uni.KHA + Uni.I, "" + Glyph.INIT_KHA_FINA_I);
        mIsolateMap.put("" + Uni.KHA + Uni.O, "" + Glyph.INIT_KHA_FINA_O);
        mIsolateMap.put("" + Uni.KHA + Uni.U, "" + Glyph.INIT_KHA_FINA_U);
        mIsolateMap.put("" + Uni.KHA + Uni.OE, "" + Glyph.INIT_KHA_FINA_OE);
        mIsolateMap.put("" + Uni.KHA + Uni.UE, "" + Glyph.INIT_KHA_FINA_UE);
        mIsolateMap.put("" + Uni.KHA + Uni.EE, "" + Glyph.INIT_KHA_FINA_EE);
        mIsolateMap.put("" + Uni.QA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_QA_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.QA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_QA_FINA_UE_FVS1);
        mIsolateMap.put("" + Uni.GA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_GA_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.GA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_GA_FINA_UE_FVS1);
        mIsolateMap.put("" + Uni.QA + Uni.FVS1 + Uni.E, "" + Glyph.INIT_QA_FVS1_FINA_E);
        mIsolateMap.put("" + Uni.QA + Uni.FVS1 + Uni.I, "" + Glyph.INIT_QA_FVS1_FINA_I);
        mIsolateMap.put("" + Uni.QA + Uni.FVS1 + Uni.OE, "" + Glyph.INIT_QA_FVS1_FINA_OE);
        mIsolateMap.put("" + Uni.QA + Uni.FVS1 + Uni.UE, "" + Glyph.INIT_QA_FVS1_FINA_UE);
        mIsolateMap.put("" + Uni.QA + Uni.FVS1 + Uni.EE, "" + Glyph.INIT_QA_FVS1_FINA_EE);
        mIsolateMap.put("" + Uni.GA + Uni.FVS1 + Uni.E, "" + Glyph.INIT_GA_FVS1_FINA_E);
        mIsolateMap.put("" + Uni.GA + Uni.FVS1 + Uni.I, "" + Glyph.INIT_GA_FVS1_FINA_I);
        mIsolateMap.put("" + Uni.GA + Uni.FVS1 + Uni.OE, "" + Glyph.INIT_GA_FVS1_FINA_OE);
        mIsolateMap.put("" + Uni.GA + Uni.FVS1 + Uni.UE, "" + Glyph.INIT_GA_FVS1_FINA_UE);
        mIsolateMap.put("" + Uni.GA + Uni.FVS1 + Uni.EE, "" + Glyph.INIT_GA_FVS1_FINA_EE);
        mIsolateMap.put("" + Uni.BA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_BA_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.BA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_BA_FINA_UE_FVS1);
        mIsolateMap.put("" + Uni.PA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_PA_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.PA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_PA_FINA_UE_FVS1);
        mIsolateMap.put("" + Uni.QA + Uni.FVS1 + Uni.OE + Uni.FVS1, "" + Glyph.INIT_QA_FVS1_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.QA + Uni.FVS1 + Uni.UE + Uni.FVS1, "" + Glyph.INIT_QA_FVS1_FINA_UE_FVS1);
        mIsolateMap.put("" + Uni.GA + Uni.FVS1 + Uni.OE + Uni.FVS1, "" + Glyph.INIT_GA_FVS1_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.GA + Uni.FVS1 + Uni.UE + Uni.FVS1, "" + Glyph.INIT_GA_FVS1_FINA_UE_FVS1);
        mIsolateMap.put("" + Uni.FA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_FA_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.FA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_FA_FINA_UE_FVS1);
        mIsolateMap.put("" + Uni.KA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_KA_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.KA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_KA_FINA_UE_FVS1);
        mIsolateMap.put("" + Uni.KHA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_KHA_FINA_OE_FVS1);
        mIsolateMap.put("" + Uni.KHA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_KHA_FINA_UE_FVS1);

        // BUU exception (no tooth on first UE)
        mIsolateMap.put("" + Uni.BA + Uni.UE + Uni.UE, "" + Glyph.INIT_BA_MEDI_U + Glyph.FINA_UE);

        // Catch other chars
        mIsolateMap.put("" + CURSOR_HOLDER, "" + CURSOR_HOLDER);
        mIsolateMap.put("" + Uni.MONGOLIAN_NIRUGU, "" + Glyph.NIRUGU);
        mIsolateMap.put("" + Uni.ZWJ, "");
        mIsolateMap.put("" + Uni.NNBS, "" + Uni.NNBS);
        mIsolateMap.put("" + Uni.MVS, "");
        mIsolateMap.put("" + Uni.FVS1, "");
        mIsolateMap.put("" + Uni.FVS2, "");
        mIsolateMap.put("" + Uni.FVS3, "");

    }

    private void initInitial() {

        // NOTE: assuming MAXIMUM_SEARCH_LENGTH = 4

        mInitialMap = new HashMap<String, String>();

        mInitialMap.put("" + Uni.A, "" + Glyph.INIT_A);
        mInitialMap.put("" + Uni.A + Uni.FVS1, "" + Glyph.INIT_A_FVS1);
        mInitialMap.put("" + Uni.E, "" + Glyph.INIT_E);
        mInitialMap.put("" + Uni.E + Uni.FVS1, "" + Glyph.INIT_E_FVS1);
        mInitialMap.put("" + Uni.I, "" + Glyph.INIT_I);
        mInitialMap.put("" + Uni.I + Uni.FVS1, "" + Glyph.INIT_I_FVS1);
        mInitialMap.put("" + Uni.O, "" + Glyph.INIT_O);
        mInitialMap.put("" + Uni.O + Uni.FVS1, "" + Glyph.INIT_O_FVS1);
        mInitialMap.put("" + Uni.U, "" + Glyph.INIT_U);
        mInitialMap.put("" + Uni.U + Uni.FVS1, "" + Glyph.INIT_U_FVS1);
        mInitialMap.put("" + Uni.OE, "" + Glyph.INIT_OE);
        mInitialMap.put("" + Uni.UE, "" + Glyph.INIT_UE);
        mInitialMap.put("" + Uni.EE, "" + Glyph.INIT_EE);
        mInitialMap.put("" + Uni.EE + Uni.FVS1, "" + Glyph.INIT_EE_FVS1);
        mInitialMap.put("" + Uni.UE + Uni.FVS1, "" + Glyph.INIT_UE_FVS1);
        mInitialMap.put("" + Uni.NA, "" + Glyph.INIT_NA);
        // TODO when is Uni.NA + Uni.FVS1 ever used?
        mInitialMap.put("" + Uni.NA + Uni.FVS1, "" + Glyph.INIT_NA_FVS1);
        mInitialMap.put("" + Uni.ANG, "" + Glyph.INIT_ANG);
        mInitialMap.put("" + Uni.BA, "" + Glyph.INIT_BA);
        mInitialMap.put("" + Uni.BA + Uni.A, "" + Glyph.INIT_BA_MEDI_A);
        mInitialMap.put("" + Uni.BA + Uni.E, "" + Glyph.INIT_BA_MEDI_E);
        mInitialMap.put("" + Uni.BA + Uni.I, "" + Glyph.INIT_BA_MEDI_I);
        mInitialMap.put("" + Uni.BA + Uni.O, "" + Glyph.INIT_BA_MEDI_O);
        mInitialMap.put("" + Uni.BA + Uni.U, "" + Glyph.INIT_BA_MEDI_U);
        mInitialMap.put("" + Uni.BA + Uni.OE, "" + Glyph.INIT_BA_MEDI_OE);
        mInitialMap.put("" + Uni.BA + Uni.UE, "" + Glyph.INIT_BA_MEDI_UE);
        mInitialMap.put("" + Uni.BA + Uni.EE, "" + Glyph.INIT_BA_MEDI_EE);
        mInitialMap.put("" + Uni.PA, "" + Glyph.INIT_PA);
        mInitialMap.put("" + Uni.PA + Uni.A, "" + Glyph.INIT_PA_MEDI_A);
        mInitialMap.put("" + Uni.PA + Uni.E, "" + Glyph.INIT_PA_MEDI_E);
        mInitialMap.put("" + Uni.PA + Uni.I, "" + Glyph.INIT_PA_MEDI_I);
        mInitialMap.put("" + Uni.PA + Uni.O, "" + Glyph.INIT_PA_MEDI_O);
        mInitialMap.put("" + Uni.PA + Uni.U, "" + Glyph.INIT_PA_MEDI_U);
        mInitialMap.put("" + Uni.PA + Uni.OE, "" + Glyph.INIT_PA_MEDI_OE);
        mInitialMap.put("" + Uni.PA + Uni.UE, "" + Glyph.INIT_PA_MEDI_UE);
        mInitialMap.put("" + Uni.PA + Uni.EE, "" + Glyph.INIT_PA_MEDI_EE);
        mInitialMap.put("" + Uni.QA, "" + Glyph.INIT_QA);
        mInitialMap.put("" + Uni.QA + Uni.FVS1, "" + Glyph.INIT_QA_FVS1);
        mInitialMap.put("" + Uni.QA + Uni.E, "" + Glyph.INIT_QA_MEDI_E);
        mInitialMap.put("" + Uni.QA + Uni.I, "" + Glyph.INIT_QA_MEDI_I);
        mInitialMap.put("" + Uni.QA + Uni.OE, "" + Glyph.INIT_QA_MEDI_OE);
        mInitialMap.put("" + Uni.QA + Uni.UE, "" + Glyph.INIT_QA_MEDI_UE);
        mInitialMap.put("" + Uni.QA + Uni.EE, "" + Glyph.INIT_QA_MEDI_EE);
        mInitialMap.put("" + Uni.GA, "" + Glyph.INIT_GA);
        mInitialMap.put("" + Uni.GA + Uni.FVS1, "" + Glyph.INIT_GA_FVS1);
        mInitialMap.put("" + Uni.GA + Uni.E, "" + Glyph.INIT_GA_MEDI_E);
        mInitialMap.put("" + Uni.GA + Uni.I, "" + Glyph.INIT_GA_MEDI_I);
        mInitialMap.put("" + Uni.GA + Uni.OE, "" + Glyph.INIT_GA_MEDI_OE);
        mInitialMap.put("" + Uni.GA + Uni.UE, "" + Glyph.INIT_GA_MEDI_UE);
        mInitialMap.put("" + Uni.GA + Uni.EE, "" + Glyph.INIT_GA_MEDI_EE);
        mInitialMap.put("" + Uni.MA, "" + Glyph.INIT_MA);
        mInitialMap.put("" + Uni.LA, "" + Glyph.INIT_LA);
        mInitialMap.put("" + Uni.SA, "" + Glyph.INIT_SA);
        mInitialMap.put("" + Uni.SHA, "" + Glyph.INIT_SHA);
        mInitialMap.put("" + Uni.TA, "" + Glyph.INIT_TA);
        mInitialMap.put("" + Uni.DA, "" + Glyph.INIT_DA);
        mInitialMap.put("" + Uni.DA + Uni.FVS1, "" + Glyph.INIT_DA_FVS1);
        mInitialMap.put("" + Uni.CHA, "" + Glyph.INIT_CHA);
        mInitialMap.put("" + Uni.JA, "" + Glyph.INIT_JA);
        mInitialMap.put("" + Uni.YA, "" + Glyph.INIT_YA);
        mInitialMap.put("" + Uni.YA + Uni.FVS1, "" + Glyph.INIT_YA_FVS1);
        mInitialMap.put("" + Uni.RA, "" + Glyph.INIT_RA);
        mInitialMap.put("" + Uni.WA, "" + Glyph.INIT_WA);
        mInitialMap.put("" + Uni.FA, "" + Glyph.INIT_FA);
        mInitialMap.put("" + Uni.FA + Uni.A, "" + Glyph.INIT_FA_MEDI_A);
        mInitialMap.put("" + Uni.FA + Uni.E, "" + Glyph.INIT_FA_MEDI_E);
        mInitialMap.put("" + Uni.FA + Uni.I, "" + Glyph.INIT_FA_MEDI_I);
        mInitialMap.put("" + Uni.FA + Uni.O, "" + Glyph.INIT_FA_MEDI_O);
        mInitialMap.put("" + Uni.FA + Uni.U, "" + Glyph.INIT_FA_MEDI_U);
        mInitialMap.put("" + Uni.FA + Uni.OE, "" + Glyph.INIT_FA_MEDI_OE);
        mInitialMap.put("" + Uni.FA + Uni.UE, "" + Glyph.INIT_FA_MEDI_UE);
        mInitialMap.put("" + Uni.FA + Uni.EE, "" + Glyph.INIT_FA_MEDI_EE);
        mInitialMap.put("" + Uni.KA, "" + Glyph.INIT_KA);
        mInitialMap.put("" + Uni.KA + Uni.A, "" + Glyph.INIT_KA_MEDI_A);
        mInitialMap.put("" + Uni.KA + Uni.E, "" + Glyph.INIT_KA_MEDI_E);
        mInitialMap.put("" + Uni.KA + Uni.I, "" + Glyph.INIT_KA_MEDI_I);
        mInitialMap.put("" + Uni.KA + Uni.O, "" + Glyph.INIT_KA_MEDI_O);
        mInitialMap.put("" + Uni.KA + Uni.U, "" + Glyph.INIT_KA_MEDI_U);
        mInitialMap.put("" + Uni.KA + Uni.OE, "" + Glyph.INIT_KA_MEDI_OE);
        mInitialMap.put("" + Uni.KA + Uni.UE, "" + Glyph.INIT_KA_MEDI_UE);
        mInitialMap.put("" + Uni.KA + Uni.EE, "" + Glyph.INIT_KA_MEDI_EE);
        mInitialMap.put("" + Uni.KHA, "" + Glyph.INIT_KHA);
        mInitialMap.put("" + Uni.KHA + Uni.A, "" + Glyph.INIT_KHA_MEDI_A);
        mInitialMap.put("" + Uni.KHA + Uni.E, "" + Glyph.INIT_KHA_MEDI_E);
        mInitialMap.put("" + Uni.KHA + Uni.I, "" + Glyph.INIT_KHA_MEDI_I);
        mInitialMap.put("" + Uni.KHA + Uni.O, "" + Glyph.INIT_KHA_MEDI_O);
        mInitialMap.put("" + Uni.KHA + Uni.U, "" + Glyph.INIT_KHA_MEDI_U);
        mInitialMap.put("" + Uni.KHA + Uni.OE, "" + Glyph.INIT_KHA_MEDI_OE);
        mInitialMap.put("" + Uni.KHA + Uni.UE, "" + Glyph.INIT_KHA_MEDI_UE);
        mInitialMap.put("" + Uni.KHA + Uni.EE, "" + Glyph.INIT_KHA_MEDI_EE);
        mInitialMap.put("" + Uni.TSA, "" + Glyph.INIT_TSA);
        mInitialMap.put("" + Uni.ZA, "" + Glyph.INIT_ZA);
        mInitialMap.put("" + Uni.HAA, "" + Glyph.INIT_HAA);
        mInitialMap.put("" + Uni.ZRA, "" + Glyph.INIT_ZRA);
        mInitialMap.put("" + Uni.LHA, "" + Glyph.INIT_LHA);
        mInitialMap.put("" + Uni.ZHI, "" + Glyph.INIT_ZHI);
        mInitialMap.put("" + Uni.CHI, "" + Glyph.INIT_CHI);
        mInitialMap.put("" + Uni.QA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_QA_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.QA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_QA_MEDI_UE_FVS1);
        mInitialMap.put("" + Uni.GA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_GA_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.GA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_GA_MEDI_UE_FVS1);
        mInitialMap.put("" + Uni.QA + Uni.FVS2, "" + Glyph.INIT_QA_FVS2);
        mInitialMap.put("" + Uni.QA + Uni.FVS3, "" + Glyph.INIT_QA_FVS3);
        mInitialMap.put("" + Uni.QA + Uni.FVS1 + Uni.E, "" + Glyph.INIT_QA_FVS1_MEDI_E);
        mInitialMap.put("" + Uni.QA + Uni.FVS1 + Uni.I, "" + Glyph.INIT_QA_FVS1_MEDI_I);
        mInitialMap.put("" + Uni.QA + Uni.FVS1 + Uni.OE, "" + Glyph.INIT_QA_FVS1_MEDI_OE);
        mInitialMap.put("" + Uni.QA + Uni.FVS1 + Uni.UE, "" + Glyph.INIT_QA_FVS1_MEDI_UE);
        mInitialMap.put("" + Uni.QA + Uni.FVS1 + Uni.EE, "" + Glyph.INIT_QA_FVS1_MEDI_EE);
        mInitialMap.put("" + Uni.GA + Uni.FVS2, "" + Glyph.INIT_GA_FVS2);
        mInitialMap.put("" + Uni.GA + Uni.FVS3, "" + Glyph.INIT_GA_FVS3);
        mInitialMap.put("" + Uni.GA + Uni.FVS1 + Uni.E, "" + Glyph.INIT_GA_FVS1_MEDI_E);
        mInitialMap.put("" + Uni.GA + Uni.FVS1 + Uni.I, "" + Glyph.INIT_GA_FVS1_MEDI_I);
        mInitialMap.put("" + Uni.GA + Uni.FVS1 + Uni.OE, "" + Glyph.INIT_GA_FVS1_MEDI_OE);
        mInitialMap.put("" + Uni.GA + Uni.FVS1 + Uni.UE, "" + Glyph.INIT_GA_FVS1_MEDI_UE);
        mInitialMap.put("" + Uni.GA + Uni.FVS1 + Uni.EE, "" + Glyph.INIT_GA_FVS1_MEDI_EE);
        mInitialMap.put("" + Uni.BA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_BA_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.BA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_BA_MEDI_UE_FVS1);
        mInitialMap.put("" + Uni.PA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_PA_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.PA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_PA_MEDI_UE_FVS1);
        mInitialMap.put("" + Uni.QA + Uni.FVS1 + Uni.OE + Uni.FVS1, "" + Glyph.INIT_QA_FVS1_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.QA + Uni.FVS1 + Uni.UE + Uni.FVS1, "" + Glyph.INIT_QA_FVS1_MEDI_UE_FVS1);
        mInitialMap.put("" + Uni.GA + Uni.FVS1 + Uni.OE + Uni.FVS1, "" + Glyph.INIT_GA_FVS1_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.GA + Uni.FVS1 + Uni.UE + Uni.FVS1, "" + Glyph.INIT_GA_FVS1_MEDI_UE_FVS1);
        mInitialMap.put("" + Uni.FA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_FA_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.FA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_FA_MEDI_UE_FVS1);
        mInitialMap.put("" + Uni.KA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_KA_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.KA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_KA_MEDI_UE_FVS1);
        mInitialMap.put("" + Uni.KHA + Uni.OE + Uni.FVS1, "" + Glyph.INIT_KHA_MEDI_OE_FVS1);
        mInitialMap.put("" + Uni.KHA + Uni.UE + Uni.FVS1, "" + Glyph.INIT_KHA_MEDI_UE_FVS1);

        // Catch other chars
        mInitialMap.put("" + CURSOR_HOLDER, "" + CURSOR_HOLDER);
        mInitialMap.put("" + Uni.MONGOLIAN_NIRUGU, "" + Glyph.NIRUGU);
        mInitialMap.put("" + Uni.ZWJ, "");
        mInitialMap.put("" + Uni.NNBS, "" + Uni.NNBS);
        mInitialMap.put("" + Uni.MVS, "");
        mInitialMap.put("" + Uni.FVS1, "");
        mInitialMap.put("" + Uni.FVS2, "");
        mInitialMap.put("" + Uni.FVS3, "");

    }

    private void initMedial() {

        // NOTE: assuming MAXIMUM_SEARCH_LENGTH = 4

        mMedialMap = new HashMap<String, String>();

        mMedialMap.put("" + Uni.A, "" + Glyph.MEDI_A);
        mMedialMap.put("" + Uni.A + Uni.FVS1, "" + Glyph.MEDI_A_FVS1);
        mMedialMap.put("" + Uni.E, "" + Glyph.MEDI_E);
        mMedialMap.put("" + Uni.I, "" + Glyph.MEDI_I);
        mMedialMap.put("" + Uni.I + Uni.FVS1, "" + Glyph.MEDI_I_FVS1);
        mMedialMap.put("" + Uni.I + Uni.FVS2, "" + Glyph.MEDI_I_FVS2);
        mMedialMap.put("" + Uni.I + Uni.FVS3, "" + Glyph.MEDI_I_FVS3);
        mMedialMap.put("" + Uni.O, "" + Glyph.MEDI_O);
        mMedialMap.put("" + Uni.O + Uni.FVS1, "" + Glyph.MEDI_O_FVS1);
        mMedialMap.put("" + Uni.U, "" + Glyph.MEDI_U);
        mMedialMap.put("" + Uni.U + Uni.FVS1, "" + Glyph.MEDI_U_FVS1);
        mMedialMap.put("" + Uni.OE, "" + Glyph.MEDI_OE);
        mMedialMap.put("" + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.OE + Uni.FVS2, "" + Glyph.MEDI_OE_FVS2);
        mMedialMap.put("" + Uni.UE, "" + Glyph.MEDI_UE);
        mMedialMap.put("" + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.UE + Uni.FVS2, "" + Glyph.MEDI_UE_FVS2);
        mMedialMap.put("" + Uni.EE, "" + Glyph.MEDI_EE);
        mMedialMap.put("" + Uni.NA, "" + Glyph.MEDI_NA);
        mMedialMap.put("" + Uni.NA + Uni.FVS1, "" + Glyph.MEDI_NA_FVS1);
        // TODO Glyph.MEDI_NA_FVS2 (long stemmed NA) is undefinied in Almas font
        // ignoring for now. (it is only used in Todo script)
        // mMedialMap.put("" + Uni.NA + Uni.FVS2, "" + Glyph.MEDI_NA_FVS1);
        // TODO !!! NON-STANDARD !!!
        // TODO Glyph.MEDI_NA_FVS3 is undefinied in Unicode
        // using Glyph.MEDI_NA as a substitute for now.
        // needed to over-ride context in name like Cholmon-Odo
        mMedialMap.put("" + Uni.NA + Uni.FVS2, "" + Glyph.MEDI_NA_FVS2);
        mMedialMap.put("" + Uni.ANG, "" + Glyph.MEDI_ANG);
        mMedialMap.put("" + Uni.ANG + Uni.QA, "" + Glyph.MEDI_ANG_MEDI_QA);
        mMedialMap.put("" + Uni.ANG + Uni.GA, "" + Glyph.MEDI_ANG_MEDI_GA);
        mMedialMap.put("" + Uni.ANG + Uni.QA + Uni.FVS1, "" + Glyph.MEDI_ANG_MEDI_QA);
        mMedialMap.put("" + Uni.ANG + Uni.GA + Uni.FVS1, "" + Glyph.MEDI_ANG_MEDI_GA);
        mMedialMap.put("" + Uni.ANG + Uni.MA, "" + Glyph.MEDI_ANG_MEDI_MA);
        mMedialMap.put("" + Uni.ANG + Uni.LA, "" + Glyph.MEDI_ANG_MEDI_LA);
        mMedialMap.put("" + Uni.ANG + Uni.NA + Uni.FVS1, "" + Glyph.MEDI_ANG_MEDI_NA_FVS1);

        mMedialMap.put("" + Uni.BA, "" + Glyph.MEDI_BA);
        mMedialMap.put("" + Uni.BA + Uni.A, "" + Glyph.MEDI_BA_MEDI_A);
        mMedialMap.put("" + Uni.BA + Uni.E, "" + Glyph.MEDI_BA_MEDI_E);
        mMedialMap.put("" + Uni.BA + Uni.I, "" + Glyph.MEDI_BA_MEDI_I);
        mMedialMap.put("" + Uni.BA + Uni.O, "" + Glyph.MEDI_BA_MEDI_O);
        mMedialMap.put("" + Uni.BA + Uni.U, "" + Glyph.MEDI_BA_MEDI_U);
        mMedialMap.put("" + Uni.BA + Uni.OE, "" + Glyph.MEDI_BA_MEDI_OE);
        mMedialMap.put("" + Uni.BA + Uni.UE, "" + Glyph.MEDI_BA_MEDI_UE);
        mMedialMap.put("" + Uni.BA + Uni.EE, "" + Glyph.MEDI_BA_MEDI_EE);
        mMedialMap.put("" + Uni.BA + Uni.MA, "" + Glyph.MEDI_BA_MEDI_MA);
        mMedialMap.put("" + Uni.BA + Uni.LA, "" + Glyph.MEDI_BA_MEDI_LA);
        mMedialMap.put("" + Uni.BA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_BA_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.BA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_BA_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.BA + Uni.QA, "" + Glyph.MEDI_BA_MEDI_QA);
        mMedialMap.put("" + Uni.BA + Uni.GA, "" + Glyph.MEDI_BA_MEDI_GA);
        mMedialMap.put("" + Uni.BA + Uni.NA + Uni.FVS1, "" + Glyph.MEDI_BA_MEDI_NA_FVS1);
        mMedialMap.put("" + Uni.PA, "" + Glyph.MEDI_PA);
        mMedialMap.put("" + Uni.PA + Uni.A, "" + Glyph.MEDI_PA_MEDI_A);
        mMedialMap.put("" + Uni.PA + Uni.E, "" + Glyph.MEDI_PA_MEDI_E);
        mMedialMap.put("" + Uni.PA + Uni.I, "" + Glyph.MEDI_PA_MEDI_I);
        mMedialMap.put("" + Uni.PA + Uni.O, "" + Glyph.MEDI_PA_MEDI_O);
        mMedialMap.put("" + Uni.PA + Uni.U, "" + Glyph.MEDI_PA_MEDI_U);
        mMedialMap.put("" + Uni.PA + Uni.OE, "" + Glyph.MEDI_PA_MEDI_OE);
        mMedialMap.put("" + Uni.PA + Uni.UE, "" + Glyph.MEDI_PA_MEDI_UE);
        mMedialMap.put("" + Uni.PA + Uni.EE, "" + Glyph.MEDI_PA_MEDI_EE);
        mMedialMap.put("" + Uni.PA + Uni.MA, "" + Glyph.MEDI_PA_MEDI_MA);
        mMedialMap.put("" + Uni.PA + Uni.LA, "" + Glyph.MEDI_PA_MEDI_LA);
        mMedialMap.put("" + Uni.PA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_PA_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.PA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_PA_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.PA + Uni.QA, "" + Glyph.MEDI_PA_MEDI_QA);
        mMedialMap.put("" + Uni.PA + Uni.GA, "" + Glyph.MEDI_PA_MEDI_GA);
        mMedialMap.put("" + Uni.PA + Uni.NA + Uni.FVS1, "" + Glyph.MEDI_PA_MEDI_NA_FVS1);
        mMedialMap.put("" + Uni.QA, "" + Glyph.MEDI_QA);
        mMedialMap.put("" + Uni.QA + Uni.FVS1, "" + Glyph.MEDI_QA_FVS1);
        mMedialMap.put("" + Uni.QA + Uni.FVS2, "" + Glyph.MEDI_QA_FVS2);
        mMedialMap.put("" + Uni.QA + Uni.E, "" + Glyph.MEDI_QA_MEDI_E);
        mMedialMap.put("" + Uni.QA + Uni.I, "" + Glyph.MEDI_QA_MEDI_I);
        mMedialMap.put("" + Uni.QA + Uni.OE, "" + Glyph.MEDI_QA_MEDI_OE);
        mMedialMap.put("" + Uni.QA + Uni.UE, "" + Glyph.MEDI_QA_MEDI_UE);
        mMedialMap.put("" + Uni.QA + Uni.EE, "" + Glyph.MEDI_QA_MEDI_EE);
        mMedialMap.put("" + Uni.QA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_QA_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.QA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_QA_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.QA + Uni.FVS3, "" + Glyph.MEDI_QA_FVS3);
        mMedialMap.put("" + Uni.QA + Uni.FVS1 + Uni.E, "" + Glyph.MEDI_QA_FVS1_MEDI_E);
        mMedialMap.put("" + Uni.QA + Uni.FVS1 + Uni.I, "" + Glyph.MEDI_QA_FVS1_MEDI_I);
        mMedialMap.put("" + Uni.QA + Uni.FVS1 + Uni.OE, "" + Glyph.MEDI_QA_FVS1_MEDI_OE);
        mMedialMap.put("" + Uni.QA + Uni.FVS1 + Uni.UE, "" + Glyph.MEDI_QA_FVS1_MEDI_UE);
        mMedialMap.put("" + Uni.QA + Uni.FVS1 + Uni.EE, "" + Glyph.MEDI_QA_FVS1_MEDI_EE);
        mMedialMap.put("" + Uni.QA + Uni.FVS1 + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_QA_FVS1_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.QA + Uni.FVS1 + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_QA_FVS1_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.GA, "" + Glyph.MEDI_GA);
        mMedialMap.put("" + Uni.GA + Uni.FVS1, "" + Glyph.MEDI_GA_FVS1);
        mMedialMap.put("" + Uni.GA + Uni.FVS2, "" + Glyph.MEDI_GA_FVS2);
        mMedialMap.put("" + Uni.GA + Uni.FVS3, "" + Glyph.MEDI_GA_FVS3);
        mMedialMap.put("" + Uni.GA + Uni.E, "" + Glyph.MEDI_GA_MEDI_E);
        mMedialMap.put("" + Uni.GA + Uni.I, "" + Glyph.MEDI_GA_MEDI_I);
        mMedialMap.put("" + Uni.GA + Uni.OE, "" + Glyph.MEDI_GA_MEDI_OE);
        mMedialMap.put("" + Uni.GA + Uni.UE, "" + Glyph.MEDI_GA_MEDI_UE);
        mMedialMap.put("" + Uni.GA + Uni.EE, "" + Glyph.MEDI_GA_MEDI_EE);
        mMedialMap.put("" + Uni.GA + Uni.FVS3 + Uni.MA, "" + Glyph.MEDI_GA_MEDI_MA);
        mMedialMap.put("" + Uni.GA + Uni.FVS3 + Uni.LA, "" + Glyph.MEDI_GA_MEDI_LA);
        mMedialMap.put("" + Uni.GA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_GA_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.GA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_GA_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.GA + Uni.FVS3 + Uni.NA + Uni.FVS1, "" + Glyph.MEDI_GA_MEDI_NA_FVS1);
        mMedialMap.put("" + Uni.GA + Uni.FVS1 + Uni.E, "" + Glyph.MEDI_GA_FVS1_MEDI_E);
        mMedialMap.put("" + Uni.GA + Uni.FVS1 + Uni.I, "" + Glyph.MEDI_GA_FVS1_MEDI_I);
        mMedialMap.put("" + Uni.GA + Uni.FVS1 + Uni.OE, "" + Glyph.MEDI_GA_FVS1_MEDI_OE);
        mMedialMap.put("" + Uni.GA + Uni.FVS1 + Uni.UE, "" + Glyph.MEDI_GA_FVS1_MEDI_UE);
        mMedialMap.put("" + Uni.GA + Uni.FVS1 + Uni.EE, "" + Glyph.MEDI_GA_FVS1_MEDI_EE);
        mMedialMap.put("" + Uni.GA + Uni.FVS1 + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_GA_FVS1_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.GA + Uni.FVS1 + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_GA_FVS1_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.MA, "" + Glyph.MEDI_MA);
        mMedialMap.put("" + Uni.MA + Uni.MA, "" + Glyph.MEDI_MA_MEDI_MA);
        mMedialMap.put("" + Uni.MA + Uni.LA, "" + Glyph.MEDI_MA_MEDI_LA);
        mMedialMap.put("" + Uni.LA, "" + Glyph.MEDI_LA);
        mMedialMap.put("" + Uni.LA + Uni.LA, "" + Glyph.MEDI_LA_MEDI_LA);
        mMedialMap.put("" + Uni.SA, "" + Glyph.MEDI_SA);
        mMedialMap.put("" + Uni.SHA, "" + Glyph.MEDI_SHA);
        mMedialMap.put("" + Uni.TA, "" + Glyph.MEDI_TA);
        mMedialMap.put("" + Uni.TA + Uni.FVS1, "" + Glyph.MEDI_TA_FVS1);
        mMedialMap.put("" + Uni.TA + Uni.FVS2, "" + Glyph.MEDI_TA_FVS2);
        mMedialMap.put("" + Uni.DA, "" + Glyph.MEDI_DA);
        mMedialMap.put("" + Uni.DA + Uni.FVS1, "" + Glyph.MEDI_DA_FVS1);
        mMedialMap.put("" + Uni.CHA, "" + Glyph.MEDI_CHA);
        mMedialMap.put("" + Uni.JA, "" + Glyph.MEDI_JA);
        mMedialMap.put("" + Uni.YA, "" + Glyph.MEDI_YA);
        mMedialMap.put("" + Uni.YA + Uni.FVS1, "" + Glyph.MEDI_YA_FVS1);
        mMedialMap.put("" + Uni.RA, "" + Glyph.MEDI_RA);
        mMedialMap.put("" + Uni.WA, "" + Glyph.MEDI_WA);
        mMedialMap.put("" + Uni.WA + Uni.FVS1, "" + Glyph.MEDI_WA_FVS1);
        mMedialMap.put("" + Uni.FA, "" + Glyph.MEDI_FA);
        mMedialMap.put("" + Uni.FA + Uni.A, "" + Glyph.MEDI_FA_MEDI_A);
        mMedialMap.put("" + Uni.FA + Uni.E, "" + Glyph.MEDI_FA_MEDI_E);
        mMedialMap.put("" + Uni.FA + Uni.I, "" + Glyph.MEDI_FA_MEDI_I);
        mMedialMap.put("" + Uni.FA + Uni.O, "" + Glyph.MEDI_FA_MEDI_O);
        mMedialMap.put("" + Uni.FA + Uni.U, "" + Glyph.MEDI_FA_MEDI_U);
        mMedialMap.put("" + Uni.FA + Uni.OE, "" + Glyph.MEDI_FA_MEDI_OE);
        mMedialMap.put("" + Uni.FA + Uni.UE, "" + Glyph.MEDI_FA_MEDI_UE);
        mMedialMap.put("" + Uni.FA + Uni.EE, "" + Glyph.MEDI_FA_MEDI_EE);
        mMedialMap.put("" + Uni.FA + Uni.MA, "" + Glyph.MEDI_FA_MEDI_MA);
        mMedialMap.put("" + Uni.FA + Uni.LA, "" + Glyph.MEDI_FA_MEDI_LA);
        mMedialMap.put("" + Uni.FA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_FA_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.FA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_FA_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.FA + Uni.QA, "" + Glyph.MEDI_FA_MEDI_QA);
        mMedialMap.put("" + Uni.FA + Uni.GA, "" + Glyph.MEDI_FA_MEDI_GA);
        mMedialMap.put("" + Uni.FA + Uni.NA + Uni.FVS1, "" + Glyph.MEDI_FA_MEDI_NA_FVS1);
        mMedialMap.put("" + Uni.KA, "" + Glyph.MEDI_KA);
        mMedialMap.put("" + Uni.KA + Uni.A, "" + Glyph.MEDI_KA_MEDI_A);
        mMedialMap.put("" + Uni.KA + Uni.E, "" + Glyph.MEDI_KA_MEDI_E);
        mMedialMap.put("" + Uni.KA + Uni.I, "" + Glyph.MEDI_KA_MEDI_I);
        mMedialMap.put("" + Uni.KA + Uni.O, "" + Glyph.MEDI_KA_MEDI_O);
        mMedialMap.put("" + Uni.KA + Uni.U, "" + Glyph.MEDI_KA_MEDI_U);
        mMedialMap.put("" + Uni.KA + Uni.OE, "" + Glyph.MEDI_KA_MEDI_OE);
        mMedialMap.put("" + Uni.KA + Uni.UE, "" + Glyph.MEDI_KA_MEDI_UE);
        mMedialMap.put("" + Uni.KA + Uni.EE, "" + Glyph.MEDI_KA_MEDI_EE);
        mMedialMap.put("" + Uni.KA + Uni.MA, "" + Glyph.MEDI_KA_MEDI_MA);
        mMedialMap.put("" + Uni.KA + Uni.LA, "" + Glyph.MEDI_KA_MEDI_LA);
        mMedialMap.put("" + Uni.KA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_KA_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.KA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_KA_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.KA + Uni.QA, "" + Glyph.MEDI_KA_MEDI_QA);
        mMedialMap.put("" + Uni.KA + Uni.GA, "" + Glyph.MEDI_KA_MEDI_GA);
        mMedialMap.put("" + Uni.KA + Uni.NA + Uni.FVS1, "" + Glyph.MEDI_KA_MEDI_NA_FVS1);
        mMedialMap.put("" + Uni.KHA, "" + Glyph.MEDI_KHA);
        mMedialMap.put("" + Uni.KHA + Uni.A, "" + Glyph.MEDI_KHA_MEDI_A);
        mMedialMap.put("" + Uni.KHA + Uni.E, "" + Glyph.MEDI_KHA_MEDI_E);
        mMedialMap.put("" + Uni.KHA + Uni.I, "" + Glyph.MEDI_KHA_MEDI_I);
        mMedialMap.put("" + Uni.KHA + Uni.O, "" + Glyph.MEDI_KHA_MEDI_O);
        mMedialMap.put("" + Uni.KHA + Uni.U, "" + Glyph.MEDI_KHA_MEDI_U);
        mMedialMap.put("" + Uni.KHA + Uni.OE, "" + Glyph.MEDI_KHA_MEDI_OE);
        mMedialMap.put("" + Uni.KHA + Uni.UE, "" + Glyph.MEDI_KHA_MEDI_UE);
        mMedialMap.put("" + Uni.KHA + Uni.EE, "" + Glyph.MEDI_KHA_MEDI_EE);
        mMedialMap.put("" + Uni.KHA + Uni.MA, "" + Glyph.MEDI_KHA_MEDI_MA);
        mMedialMap.put("" + Uni.KHA + Uni.LA, "" + Glyph.MEDI_KHA_MEDI_LA);
        mMedialMap.put("" + Uni.KHA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_KHA_MEDI_OE_FVS1);
        mMedialMap.put("" + Uni.KHA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_KHA_MEDI_UE_FVS1);
        mMedialMap.put("" + Uni.KHA + Uni.QA, "" + Glyph.MEDI_KHA_MEDI_QA);
        mMedialMap.put("" + Uni.KHA + Uni.GA, "" + Glyph.MEDI_KHA_MEDI_GA);
        mMedialMap.put("" + Uni.KHA + Uni.NA + Uni.FVS1, "" + Glyph.MEDI_KHA_MEDI_NA_FVS1);
        mMedialMap.put("" + Uni.TSA, "" + Glyph.MEDI_TSA);
        mMedialMap.put("" + Uni.ZA, "" + Glyph.MEDI_ZA);
        mMedialMap.put("" + Uni.HAA, "" + Glyph.MEDI_HAA);
        mMedialMap.put("" + Uni.ZRA, "" + Glyph.MEDI_ZRA);
        mMedialMap.put("" + Uni.LHA, "" + Glyph.MEDI_LHA);
        mMedialMap.put("" + Uni.ZHI, "" + Glyph.MEDI_ZHI);
        mMedialMap.put("" + Uni.CHI, "" + Glyph.MEDI_CHI);

        // MVS
        mMedialMap.put("" + Uni.NA + Uni.MVS, "" + Glyph.FINA_NA_FVS1);
        mMedialMap.put("" + Uni.ANG + Uni.QA + Uni.MVS, "" + Glyph.MEDI_ANG_FINA_QA);
        mMedialMap.put("" + Uni.ANG + Uni.FVS1 + Uni.QA + Uni.MVS, "" + Glyph.MEDI_ANG_FINA_QA);
        mMedialMap.put("" + Uni.ANG + Uni.FVS1 + Uni.GA + Uni.MVS, "" + Glyph.MEDI_ANG_FINA_GA);
        mMedialMap.put("" + Uni.ANG + Uni.GA + Uni.MVS, "" + Glyph.MEDI_ANG_FINA_GA);
        mMedialMap.put("" + Uni.BA + Uni.MVS, "" + Glyph.FINA_BA);
        mMedialMap.put("" + Uni.PA + Uni.MVS, "" + Glyph.FINA_PA);
        mMedialMap.put("" + Uni.QA + Uni.MVS, "" + Glyph.FINA_QA);
        mMedialMap.put("" + Uni.GA + Uni.MVS, "" + Glyph.FINA_GA_FVS3);
        mMedialMap.put("" + Uni.MA + Uni.MVS, "" + Glyph.FINA_MA);
        mMedialMap.put("" + Uni.LA + Uni.MVS, "" + Glyph.FINA_LA);
        mMedialMap.put("" + Uni.SA + Uni.MVS, "" + Glyph.FINA_SA);
        mMedialMap.put("" + Uni.SA + Uni.FVS1 + Uni.MVS, "" + Glyph.FINA_SA_FVS1);
        mMedialMap.put("" + Uni.SHA + Uni.MVS, "" + Glyph.FINA_SHA);
        mMedialMap.put("" + Uni.TA + Uni.MVS, "" + Glyph.FINA_TA);
        mMedialMap.put("" + Uni.DA + Uni.MVS, "" + Glyph.FINA_DA_FVS1);
        mMedialMap.put("" + Uni.CHA + Uni.MVS, "" + Glyph.FINA_CHA);
        mMedialMap.put("" + Uni.JA + Uni.MVS, "" + Glyph.FINA_JA_FVS1);
        mMedialMap.put("" + Uni.YA + Uni.MVS, "" + Glyph.FINA_YA);
        mMedialMap.put("" + Uni.I + Uni.MVS, "" + Glyph.FINA_YA); // I may be a substitute for YA
        mMedialMap.put("" + Uni.RA + Uni.MVS, "" + Glyph.FINA_RA);
        mMedialMap.put("" + Uni.WA + Uni.MVS, "" + Glyph.FINA_WA);
        mMedialMap.put("" + Uni.FA + Uni.MVS, "" + Glyph.FINA_FA);
        mMedialMap.put("" + Uni.KA + Uni.MVS, "" + Glyph.FINA_KA);
        mMedialMap.put("" + Uni.KHA + Uni.MVS, "" + Glyph.FINA_KHA);
        mMedialMap.put("" + Uni.TSA + Uni.MVS, "" + Glyph.FINA_TSA);
        mMedialMap.put("" + Uni.ZA + Uni.MVS, "" + Glyph.FINA_ZA);
        mMedialMap.put("" + Uni.HAA + Uni.MVS, "" + Glyph.FINA_HAA);
        mMedialMap.put("" + Uni.ZRA + Uni.MVS, "" + Glyph.FINA_ZRA);
        mMedialMap.put("" + Uni.LHA + Uni.MVS, "" + Glyph.FINA_LHA);
        mMedialMap.put("" + Uni.ZHI + Uni.MVS, "" + Glyph.FINA_ZHI);
        mMedialMap.put("" + Uni.CHI + Uni.MVS, "" + Glyph.FINA_CHI);

        // Catch other chars
        mMedialMap.put("" + CURSOR_HOLDER, "" + CURSOR_HOLDER);
        mMedialMap.put("" + Uni.MONGOLIAN_NIRUGU, "" + Glyph.NIRUGU);
        mMedialMap.put("" + Uni.ZWJ, "");
        mMedialMap.put("" + Uni.NNBS, "" + Uni.NNBS);
        mMedialMap.put("" + Uni.MVS, "");
        mMedialMap.put("" + Uni.FVS1, "");
        mMedialMap.put("" + Uni.FVS2, "");
        mMedialMap.put("" + Uni.FVS3, "");

    }

    private void initFinal() {

        // NOTE: assuming MAXIMUM_SEARCH_LENGTH = 4

        mFinalMap = new HashMap<String, String>();

        mFinalMap.put("" + Uni.A, "" + Glyph.FINA_A);
        mFinalMap.put("" + Uni.A + Uni.FVS1, "" + Glyph.FINA_A_FVS1);
        mFinalMap.put("" + Uni.A + Uni.FVS2, "" + Glyph.FINA_A_FVS2);
        mFinalMap.put("" + Uni.E, "" + Glyph.FINA_E);
        mFinalMap.put("" + Uni.E + Uni.FVS1, "" + Glyph.FINA_E_FVS1);
        mFinalMap.put("" + Uni.E + Uni.FVS2, "" + Glyph.FINA_E_FVS2);
        mFinalMap.put("" + Uni.I, "" + Glyph.FINA_I);
        mFinalMap.put("" + Uni.O, "" + Glyph.FINA_O);
        mFinalMap.put("" + Uni.O + Uni.FVS1, "" + Glyph.FINA_O_FVS1);
        mFinalMap.put("" + Uni.U, "" + Glyph.FINA_U);
        mFinalMap.put("" + Uni.U + Uni.FVS1, "" + Glyph.FINA_U_FVS1);
        mFinalMap.put("" + Uni.OE, "" + Glyph.FINA_OE);
        mFinalMap.put("" + Uni.OE + Uni.FVS1, "" + Glyph.FINA_OE_FVS1);
        mFinalMap.put("" + Uni.UE, "" + Glyph.FINA_UE);
        mFinalMap.put("" + Uni.UE + Uni.FVS1, "" + Glyph.FINA_UE_FVS1);
        mFinalMap.put("" + Uni.EE, "" + Glyph.FINA_EE);
        mFinalMap.put("" + Uni.NA, "" + Glyph.FINA_NA);
        mFinalMap.put("" + Uni.NA + Uni.FVS1, "" + Glyph.FINA_NA_FVS1);
        mFinalMap.put("" + Uni.ANG, "" + Glyph.FINA_ANG);
        mFinalMap.put("" + Uni.BA, "" + Glyph.FINA_BA);
        mFinalMap.put("" + Uni.BA + Uni.A, "" + Glyph.MEDI_BA_FINA_A);
        mFinalMap.put("" + Uni.BA + Uni.E, "" + Glyph.MEDI_BA_FINA_E);
        mFinalMap.put("" + Uni.BA + Uni.I, "" + Glyph.MEDI_BA_FINA_I);
        mFinalMap.put("" + Uni.BA + Uni.O, "" + Glyph.MEDI_BA_FINA_O);
        mFinalMap.put("" + Uni.BA + Uni.U, "" + Glyph.MEDI_BA_FINA_U);
        mFinalMap.put("" + Uni.BA + Uni.OE, "" + Glyph.MEDI_BA_FINA_OE);
        mFinalMap.put("" + Uni.BA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_BA_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.BA + Uni.UE, "" + Glyph.MEDI_BA_FINA_UE);
        mFinalMap.put("" + Uni.BA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_BA_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.BA + Uni.EE, "" + Glyph.MEDI_BA_FINA_EE);
        mFinalMap.put("" + Uni.PA, "" + Glyph.FINA_PA);
        mFinalMap.put("" + Uni.PA + Uni.A, "" + Glyph.MEDI_PA_FINA_A);
        mFinalMap.put("" + Uni.PA + Uni.E, "" + Glyph.MEDI_PA_FINA_E);
        mFinalMap.put("" + Uni.PA + Uni.I, "" + Glyph.MEDI_PA_FINA_I);
        mFinalMap.put("" + Uni.PA + Uni.O, "" + Glyph.MEDI_PA_FINA_O);
        mFinalMap.put("" + Uni.PA + Uni.U, "" + Glyph.MEDI_PA_FINA_U);
        mFinalMap.put("" + Uni.PA + Uni.OE, "" + Glyph.MEDI_PA_FINA_OE);
        mFinalMap.put("" + Uni.PA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_PA_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.PA + Uni.UE, "" + Glyph.MEDI_PA_FINA_UE);
        mFinalMap.put("" + Uni.PA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_PA_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.PA + Uni.EE, "" + Glyph.MEDI_PA_FINA_EE);
        mFinalMap.put("" + Uni.QA, "" + Glyph.FINA_QA);
        mFinalMap.put("" + Uni.QA + Uni.FVS1, "" + Glyph.FINA_QA_FVS1);
        mFinalMap.put("" + Uni.QA + Uni.FVS2, "" + Glyph.FINA_QA_FVS2);
        mFinalMap.put("" + Uni.QA + Uni.E, "" + Glyph.MEDI_QA_FINA_E);
        mFinalMap.put("" + Uni.QA + Uni.I, "" + Glyph.MEDI_QA_FINA_I);
        mFinalMap.put("" + Uni.QA + Uni.OE, "" + Glyph.MEDI_QA_FINA_OE);
        mFinalMap.put("" + Uni.QA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_QA_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.QA + Uni.UE, "" + Glyph.MEDI_QA_FINA_UE);
        mFinalMap.put("" + Uni.QA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_QA_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.QA + Uni.EE, "" + Glyph.MEDI_QA_FINA_EE);
        mFinalMap.put("" + Uni.GA, "" + Glyph.FINA_GA);
        mFinalMap.put("" + Uni.GA + Uni.FVS1, "" + Glyph.FINA_GA_FVS1);
        mFinalMap.put("" + Uni.GA + Uni.FVS2, "" + Glyph.FINA_GA_FVS2);
        // TODO The FSV3 is just to make it compatible with Baiti
        mFinalMap.put("" + Uni.GA + Uni.FVS3, "" + Glyph.FINA_GA_FVS3);
        mFinalMap.put("" + Uni.GA + Uni.E, "" + Glyph.MEDI_GA_FINA_E);
        mFinalMap.put("" + Uni.GA + Uni.I, "" + Glyph.MEDI_GA_FINA_I);
        mFinalMap.put("" + Uni.GA + Uni.OE, "" + Glyph.MEDI_GA_FINA_OE);
        mFinalMap.put("" + Uni.GA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_GA_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.GA + Uni.UE, "" + Glyph.MEDI_GA_FINA_UE);
        mFinalMap.put("" + Uni.GA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_GA_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.GA + Uni.EE, "" + Glyph.MEDI_GA_FINA_EE);
        mFinalMap.put("" + Uni.MA, "" + Glyph.FINA_MA);
        mFinalMap.put("" + Uni.LA, "" + Glyph.FINA_LA);
        mFinalMap.put("" + Uni.SA, "" + Glyph.FINA_SA);
        mFinalMap.put("" + Uni.SHA, "" + Glyph.FINA_SHA);
        mFinalMap.put("" + Uni.TA, "" + Glyph.FINA_TA);
        mFinalMap.put("" + Uni.DA, "" + Glyph.FINA_DA);
        mFinalMap.put("" + Uni.DA + Uni.FVS1, "" + Glyph.FINA_DA_FVS1);
        mFinalMap.put("" + Uni.CHA, "" + Glyph.FINA_CHA);
        mFinalMap.put("" + Uni.JA, "" + Glyph.FINA_JA);
        mFinalMap.put("" + Uni.JA + Uni.FVS1, "" + Glyph.FINA_JA_FVS1);
        mFinalMap.put("" + Uni.YA, "" + Glyph.FINA_YA);
        mFinalMap.put("" + Uni.RA, "" + Glyph.FINA_RA);
        mFinalMap.put("" + Uni.WA, "" + Glyph.FINA_WA);
        mFinalMap.put("" + Uni.WA + Uni.FVS1, "" + Glyph.FINA_WA_FVS1);
        mFinalMap.put("" + Uni.FA, "" + Glyph.FINA_FA);
        mFinalMap.put("" + Uni.FA + Uni.A, "" + Glyph.MEDI_FA_FINA_A);
        mFinalMap.put("" + Uni.FA + Uni.E, "" + Glyph.MEDI_FA_FINA_E);
        mFinalMap.put("" + Uni.FA + Uni.I, "" + Glyph.MEDI_FA_FINA_I);
        mFinalMap.put("" + Uni.FA + Uni.O, "" + Glyph.MEDI_FA_FINA_O);
        mFinalMap.put("" + Uni.FA + Uni.U, "" + Glyph.MEDI_FA_FINA_U);
        mFinalMap.put("" + Uni.FA + Uni.OE, "" + Glyph.MEDI_FA_FINA_OE);
        mFinalMap.put("" + Uni.FA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_FA_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.FA + Uni.UE, "" + Glyph.MEDI_FA_FINA_UE);
        mFinalMap.put("" + Uni.FA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_FA_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.FA + Uni.EE, "" + Glyph.MEDI_FA_FINA_EE);
        mFinalMap.put("" + Uni.KA, "" + Glyph.FINA_KA);
        mFinalMap.put("" + Uni.KA + Uni.A, "" + Glyph.MEDI_KA_FINA_A);
        mFinalMap.put("" + Uni.KA + Uni.E, "" + Glyph.MEDI_KA_FINA_E);
        mFinalMap.put("" + Uni.KA + Uni.I, "" + Glyph.MEDI_KA_FINA_I);
        mFinalMap.put("" + Uni.KA + Uni.O, "" + Glyph.MEDI_KA_FINA_O);
        mFinalMap.put("" + Uni.KA + Uni.U, "" + Glyph.MEDI_KA_FINA_U);
        mFinalMap.put("" + Uni.KA + Uni.OE, "" + Glyph.MEDI_KA_FINA_OE);
        mFinalMap.put("" + Uni.KA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_KA_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.KA + Uni.UE, "" + Glyph.MEDI_KA_FINA_UE);
        mFinalMap.put("" + Uni.KA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_KA_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.KA + Uni.EE, "" + Glyph.MEDI_KA_FINA_EE);
        mFinalMap.put("" + Uni.KHA, "" + Glyph.FINA_KHA);
        mFinalMap.put("" + Uni.KHA + Uni.A, "" + Glyph.MEDI_KHA_FINA_A);
        mFinalMap.put("" + Uni.KHA + Uni.E, "" + Glyph.MEDI_KHA_FINA_E);
        mFinalMap.put("" + Uni.KHA + Uni.I, "" + Glyph.MEDI_KHA_FINA_I);
        mFinalMap.put("" + Uni.KHA + Uni.O, "" + Glyph.MEDI_KHA_FINA_O);
        mFinalMap.put("" + Uni.KHA + Uni.U, "" + Glyph.MEDI_KHA_FINA_U);
        mFinalMap.put("" + Uni.KHA + Uni.OE, "" + Glyph.MEDI_KHA_FINA_OE);
        mFinalMap.put("" + Uni.KHA + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_KHA_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.KHA + Uni.UE, "" + Glyph.MEDI_KHA_FINA_UE);
        mFinalMap.put("" + Uni.KHA + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_KHA_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.KHA + Uni.EE, "" + Glyph.MEDI_KHA_FINA_EE);
        mFinalMap.put("" + Uni.TSA, "" + Glyph.FINA_TSA);
        mFinalMap.put("" + Uni.ZA, "" + Glyph.FINA_ZA);
        mFinalMap.put("" + Uni.HAA, "" + Glyph.FINA_HAA);
        mFinalMap.put("" + Uni.ZRA, "" + Glyph.FINA_ZRA);
        mFinalMap.put("" + Uni.LHA, "" + Glyph.FINA_LHA);
        mFinalMap.put("" + Uni.ZHI, "" + Glyph.FINA_ZHI);
        mFinalMap.put("" + Uni.CHI, "" + Glyph.FINA_CHI);
        mFinalMap.put("" + Uni.SA + Uni.FVS1, "" + Glyph.FINA_SA_FVS1);
        mFinalMap.put("" + Uni.SA + Uni.FVS2, "" + Glyph.FINA_SA_FVS2);
        mFinalMap.put("" + Uni.BA + Uni.FVS1, "" + Glyph.FINA_BA_FVS1);
        mFinalMap.put("" + Uni.ANG + Uni.QA, "" + Glyph.MEDI_ANG_FINA_QA);
        mFinalMap.put("" + Uni.ANG + Uni.GA, "" + Glyph.MEDI_ANG_FINA_GA);
        mFinalMap.put("" + Uni.QA + Uni.FVS1 + Uni.E, "" + Glyph.MEDI_QA_FVS1_FINA_E);
        mFinalMap.put("" + Uni.QA + Uni.FVS1 + Uni.I, "" + Glyph.MEDI_QA_FVS1_FINA_I);
        mFinalMap.put("" + Uni.QA + Uni.FVS1 + Uni.OE, "" + Glyph.MEDI_QA_FVS1_FINA_OE);
        mFinalMap.put("" + Uni.QA + Uni.FVS1 + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_QA_FVS1_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.QA + Uni.FVS1 + Uni.UE, "" + Glyph.MEDI_QA_FVS1_FINA_UE);
        mFinalMap.put("" + Uni.QA + Uni.FVS1 + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_QA_FVS1_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.QA + Uni.FVS1 + Uni.EE, "" + Glyph.MEDI_QA_FVS1_FINA_EE);
        mFinalMap.put("" + Uni.NA + Uni.FVS2, "" + Glyph.FINA_NA_FVS2);
        mFinalMap.put("" + Uni.GA + Uni.FVS1 + Uni.E, "" + Glyph.MEDI_GA_FVS1_FINA_E);
        mFinalMap.put("" + Uni.GA + Uni.FVS1 + Uni.I, "" + Glyph.MEDI_GA_FVS1_FINA_I);
        mFinalMap.put("" + Uni.GA + Uni.FVS1 + Uni.OE, "" + Glyph.MEDI_GA_FVS1_FINA_OE);
        mFinalMap.put("" + Uni.GA + Uni.FVS1 + Uni.OE + Uni.FVS1, "" + Glyph.MEDI_GA_FVS1_FINA_OE_FVS1);
        mFinalMap.put("" + Uni.GA + Uni.FVS1 + Uni.UE, "" + Glyph.MEDI_GA_FVS1_FINA_UE);
        mFinalMap.put("" + Uni.GA + Uni.FVS1 + Uni.UE + Uni.FVS1, "" + Glyph.MEDI_GA_FVS1_FINA_UE_FVS1);
        mFinalMap.put("" + Uni.GA + Uni.FVS1 + Uni.EE, "" + Glyph.MEDI_GA_FVS1_FINA_EE);

        // Final Vowel+YI rule (drop the Y)
        // (preFormatter catches final Consonant+YI)
        // mFinalMap.put("" + Uni.YA + Uni.I, "" + Glyph.FINA_I);
        // (disabling this rule because it messes up words like namayi
        // and chimayi. Is there a reason for it?)

        // MVS
        // TODO handle MVS in preFormatter()?
        mFinalMap.put("" + Uni.NA + Uni.MVS, "" + Glyph.FINA_NA_FVS1);
        mFinalMap.put("" + Uni.ANG + Uni.QA + Uni.MVS, "" + Glyph.MEDI_ANG_FINA_QA);
        mFinalMap.put("" + Uni.ANG + Uni.FVS1 + Uni.QA + Uni.MVS, "" + Glyph.MEDI_ANG_FINA_QA);
        mFinalMap.put("" + Uni.ANG + Uni.FVS1 + Uni.GA + Uni.MVS, "" + Glyph.MEDI_ANG_FINA_GA);
        mFinalMap.put("" + Uni.ANG + Uni.GA + Uni.MVS, "" + Glyph.MEDI_ANG_FINA_GA);
        mFinalMap.put("" + Uni.QA + Uni.MVS, "" + Glyph.FINA_QA);
        mFinalMap.put("" + Uni.GA + Uni.MVS, "" + Glyph.FINA_GA_FVS3);
        mFinalMap.put("" + Uni.MA + Uni.MVS, "" + Glyph.FINA_MA);
        mFinalMap.put("" + Uni.LA + Uni.MVS, "" + Glyph.FINA_LA);
        mFinalMap.put("" + Uni.JA + Uni.MVS, "" + Glyph.FINA_JA_FVS1);
        mFinalMap.put("" + Uni.YA + Uni.MVS, "" + Glyph.FINA_YA);
        mFinalMap.put("" + Uni.I + Uni.MVS, "" + Glyph.FINA_YA); // I may be a substitute for YA
        mFinalMap.put("" + Uni.RA + Uni.MVS, "" + Glyph.FINA_RA);
        mFinalMap.put("" + Uni.WA + Uni.MVS, "" + Glyph.FINA_WA);

        // Catch other chars
        mFinalMap.put("" + CURSOR_HOLDER, "" + CURSOR_HOLDER);
        mFinalMap.put("" + Uni.MONGOLIAN_NIRUGU, "" + Glyph.NIRUGU);
        mFinalMap.put("" + Uni.ZWJ, "");
        mFinalMap.put("" + Uni.NNBS, "" + Uni.NNBS);
        mFinalMap.put("" + Uni.MVS, "");
        mFinalMap.put("" + Uni.FVS1, "");
        mFinalMap.put("" + Uni.FVS2, "");
        mFinalMap.put("" + Uni.FVS3, "");

    }

    private void initSuffixes() {

        mSuffixMap = new HashMap<String, String>();

        // Vocative Case
        mSuffixMap.put("" + Uni.A, "" + Glyph.FINA_A_FVS1);
        mSuffixMap.put("" + Uni.E, "" + Glyph.FINA_E_FVS1);

        // Genetive Case
        // YIN
        mSuffixMap.put("" + Uni.YA + Uni.I + Uni.NA, "" + Glyph.INIT_YA_FVS1 + Glyph.MEDI_I
                + Glyph.FINA_NA);
        // UN
        mSuffixMap.put("" + Uni.U + Uni.NA, "" + Glyph.INIT_U_FVS1 + Glyph.FINA_NA);
        // UEN
        mSuffixMap.put("" + Uni.UE + Uni.NA, "" + Glyph.INIT_UE_FVS1 + Glyph.FINA_NA);
        // U
        mSuffixMap.put("" + Uni.U, "" + Glyph.ISOL_U_FVS2);
        // UE
        mSuffixMap.put("" + Uni.UE, "" + Glyph.ISOL_UE_FVS3);

        // Accusative Case
        // I
        mSuffixMap.put("" + Uni.I, "" + Glyph.ISOL_I_FVS1);
        // YI
        mSuffixMap.put("" + Uni.YA + Uni.I, "" + Glyph.INIT_YA_FVS1 + Glyph.FINA_I);

        // Dative-Locative Case
        // DU
        mSuffixMap.put("" + Uni.DA + Uni.U, "" + Glyph.INIT_DA_FVS1 + Glyph.FINA_U);
        // DUE
        mSuffixMap.put("" + Uni.DA + Uni.UE, "" + Glyph.INIT_DA_FVS1 + Glyph.FINA_UE);
        // TU
        mSuffixMap.put("" + Uni.TA + Uni.U, "" + Glyph.INIT_TA + Glyph.FINA_U);
        // TUE
        mSuffixMap.put("" + Uni.TA + Uni.UE, "" + Glyph.INIT_TA + Glyph.FINA_UE);
        // DUR
        mSuffixMap.put("" + Uni.DA + Uni.U + Uni.RA, "" + Glyph.INIT_DA_FVS1 + Glyph.MEDI_U
                + Glyph.FINA_RA);
        // DUER
        mSuffixMap.put("" + Uni.DA + Uni.UE + Uni.RA, "" + Glyph.INIT_DA_FVS1 + Glyph.MEDI_UE
                + Glyph.FINA_RA);
        // TUR
        mSuffixMap.put("" + Uni.TA + Uni.U + Uni.RA, "" + Glyph.INIT_TA + Glyph.MEDI_U
                + Glyph.FINA_RA);
        // TUER
        mSuffixMap.put("" + Uni.TA + Uni.UE + Uni.RA, "" + Glyph.INIT_TA + Glyph.MEDI_UE
                + Glyph.FINA_RA);
        // DAQI
        mSuffixMap.put("" + Uni.DA + Uni.A + Uni.QA + Uni.I, "" + Glyph.INIT_DA_FVS1 + Glyph.MEDI_A
                + Glyph.MEDI_QA_FINA_I);
        // DEQI
        mSuffixMap.put("" + Uni.DA + Uni.E + Uni.QA + Uni.I, "" + Glyph.INIT_DA_FVS1 + Glyph.MEDI_E
                + Glyph.MEDI_QA_FINA_I);

        // Ablative Case
        // ACHA
        mSuffixMap.put("" + Uni.A + Uni.CHA + Uni.A, "" + Glyph.INIT_A_FVS1 + Glyph.MEDI_CHA
                + Glyph.FINA_A);
        // ECHE
        mSuffixMap.put("" + Uni.E + Uni.CHA + Uni.E, "" + Glyph.INIT_E + Glyph.MEDI_CHA
                + Glyph.FINA_E);

        // Instrumental Case
        // BAR
        mSuffixMap.put("" + Uni.BA + Uni.A + Uni.RA, "" + Glyph.INIT_BA_MEDI_A + Glyph.FINA_RA);
        // BER
        mSuffixMap.put("" + Uni.BA + Uni.E + Uni.RA, "" + Glyph.INIT_BA_MEDI_E + Glyph.FINA_RA);
        // IYAR
        mSuffixMap.put("" + Uni.I + Uni.YA + Uni.A + Uni.RA, "" + Glyph.INIT_I_FVS1 + Glyph.MEDI_I
                + Glyph.MEDI_A + Glyph.FINA_RA);
        // IYER
        mSuffixMap.put("" + Uni.I + Uni.YA + Uni.E + Uni.RA, "" + Glyph.INIT_I_FVS1 + Glyph.MEDI_I
                + Glyph.MEDI_E + Glyph.FINA_RA);

        // Comitative Case
        // TAI
        mSuffixMap.put("" + Uni.TA + Uni.A + Uni.I, "" + Glyph.INIT_TA + Glyph.MEDI_A
                + Glyph.FINA_I);
        mSuffixMap.put("" + Uni.TA + Uni.A + Uni.YA + Uni.I, "" + Glyph.INIT_TA + Glyph.MEDI_A
                + Glyph.FINA_I);
        // TEI
        mSuffixMap.put("" + Uni.TA + Uni.E + Uni.I, "" + Glyph.INIT_TA + Glyph.MEDI_E
                + Glyph.FINA_I);
        mSuffixMap.put("" + Uni.TA + Uni.E + Uni.YA + Uni.I, "" + Glyph.INIT_TA + Glyph.MEDI_E
                + Glyph.FINA_I);
        // LUG-A
        mSuffixMap.put("" + Uni.LA + Uni.U + Uni.GA + Uni.MVS + Uni.A, "" + Glyph.INIT_LA
                + Glyph.MEDI_U + Glyph.FINA_GA_FVS3 + Glyph.FINA_A_FVS2);
        // LUEGE
        mSuffixMap.put("" + Uni.LA + Uni.UE + Uni.GA + Uni.E, "" + Glyph.INIT_LA + Glyph.MEDI_UE
                + Glyph.MEDI_GA_FINA_E);

        // Reflexive Case
        // BAN
        mSuffixMap.put("" + Uni.BA + Uni.A + Uni.NA, "" + Glyph.INIT_BA_MEDI_A + Glyph.FINA_NA);
        // BEN
        mSuffixMap.put("" + Uni.BA + Uni.E + Uni.NA, "" + Glyph.INIT_BA_MEDI_E + Glyph.FINA_NA);
        // IYAN
        mSuffixMap.put("" + Uni.I + Uni.YA + Uni.A + Uni.NA, "" + Glyph.INIT_I_FVS1 + Glyph.MEDI_I
                + Glyph.MEDI_A + Glyph.FINA_NA);
        // IYEN
        mSuffixMap.put("" + Uni.I + Uni.YA + Uni.E + Uni.NA, "" + Glyph.INIT_I_FVS1 + Glyph.MEDI_I
                + Glyph.MEDI_E + Glyph.FINA_NA);

        // Reflexive+Accusative
        // YUGAN
        mSuffixMap.put("" + Uni.YA + Uni.U + Uni.GA + Uni.A + Uni.NA, "" + Glyph.INIT_YA
                + Glyph.MEDI_U + Glyph.MEDI_GA_FVS1 + Glyph.MEDI_A + Glyph.FINA_NA);
        // YUEGEN
        mSuffixMap.put("" + Uni.YA + Uni.UE + Uni.GA + Uni.E + Uni.NA, "" + Glyph.INIT_YA
                + Glyph.MEDI_UE + Glyph.MEDI_GA_MEDI_E + Glyph.FINA_NA);

        // Reflexive+Dative-Locative
        // DAGAN
        mSuffixMap.put("" + Uni.DA + Uni.A + Uni.GA + Uni.A + Uni.NA, "" + Glyph.INIT_DA_FVS1
                + Glyph.MEDI_A + Glyph.MEDI_GA_FVS1 + Glyph.MEDI_A + Glyph.FINA_NA);
        // DEGEN
        mSuffixMap.put("" + Uni.DA + Uni.E + Uni.GA + Uni.E + Uni.NA, "" + Glyph.INIT_DA_FVS1
                + Glyph.MEDI_E + Glyph.MEDI_GA_MEDI_E + Glyph.FINA_NA);
        // TAGAN
        mSuffixMap.put("" + Uni.TA + Uni.A + Uni.GA + Uni.A + Uni.NA, "" + Glyph.INIT_TA
                + Glyph.MEDI_A + Glyph.MEDI_GA_FVS1 + Glyph.MEDI_A + Glyph.FINA_NA);
        // TEGEN
        mSuffixMap.put("" + Uni.TA + Uni.E + Uni.GA + Uni.E + Uni.NA, "" + Glyph.INIT_TA
                + Glyph.MEDI_E + Glyph.MEDI_GA_MEDI_E + Glyph.FINA_NA);

        // Reflexive+Ablative
        // ACHAGAN
        mSuffixMap.put("" + Uni.A + Uni.CHA + Uni.A + Uni.GA + Uni.A + Uni.NA, ""
                + Glyph.INIT_A_FVS1 + Glyph.MEDI_CHA + Glyph.MEDI_A + Glyph.MEDI_GA_FVS1
                + Glyph.MEDI_A + Glyph.FINA_NA);
        // ECHEGEN
        mSuffixMap.put("" + Uni.E + Uni.CHA + Uni.E + Uni.GA + Uni.E + Uni.NA, "" + Glyph.INIT_E
                + Glyph.MEDI_CHA + Glyph.MEDI_E + Glyph.MEDI_GA_MEDI_E + Glyph.FINA_NA);

        // Reflexive+Comitative
        // TAIGAN
        mSuffixMap.put("" + Uni.TA + Uni.A + Uni.I + Uni.GA + Uni.A + Uni.NA, "" + Glyph.INIT_TA
                + Glyph.MEDI_A + Glyph.MEDI_I_FVS3 + Glyph.MEDI_GA_FVS1 + Glyph.MEDI_A
                + Glyph.FINA_NA);
        mSuffixMap.put("" + Uni.TA + Uni.A + Uni.YA + Uni.I + Uni.GA + Uni.A + Uni.NA, ""
                + Glyph.INIT_TA + Glyph.MEDI_A + Glyph.MEDI_I_FVS3 + Glyph.MEDI_GA_FVS1
                + Glyph.MEDI_A + Glyph.FINA_NA);
        // TEIGEN
        mSuffixMap.put("" + Uni.TA + Uni.E + Uni.I + Uni.GA + Uni.E + Uni.NA, "" + Glyph.INIT_TA
                + Glyph.MEDI_E + Glyph.MEDI_I_FVS3 + Glyph.MEDI_GA_MEDI_E + Glyph.FINA_NA);
        mSuffixMap.put("" + Uni.TA + Uni.E + Uni.YA + Uni.I + Uni.GA + Uni.E + Uni.NA, ""
                + Glyph.INIT_TA + Glyph.MEDI_E + Glyph.MEDI_I_FVS3 + Glyph.MEDI_GA_MEDI_E
                + Glyph.FINA_NA);

        // Plural
        // UD
        mSuffixMap.put("" + Uni.U + Uni.DA, "" + Glyph.INIT_U_FVS1 + Glyph.FINA_DA);
        // UED
        mSuffixMap.put("" + Uni.UE + Uni.DA, "" + Glyph.INIT_UE_FVS1 + Glyph.FINA_DA);
        // NUGUD
        mSuffixMap.put("" + Uni.NA + Uni.U + Uni.GA + Uni.U + Uni.DA, "" + Glyph.INIT_NA
                + Glyph.MEDI_U + Glyph.MEDI_GA_FVS1 + Glyph.MEDI_U + Glyph.FINA_DA);
        // NUEGUED
        mSuffixMap.put("" + Uni.NA + Uni.UE + Uni.GA + Uni.UE + Uni.DA, "" + Glyph.INIT_NA
                + Glyph.MEDI_UE + Glyph.MEDI_GA_MEDI_UE + Glyph.FINA_DA);
        // NAR
        mSuffixMap.put("" + Uni.NA + Uni.A + Uni.RA, "" + Glyph.INIT_NA + Glyph.MEDI_A
                + Glyph.FINA_RA);
        // NER
        mSuffixMap.put("" + Uni.NA + Uni.E + Uni.RA, "" + Glyph.INIT_NA + Glyph.MEDI_E
                + Glyph.FINA_RA);

        // Question partical
        // UU
        mSuffixMap.put("" + Uni.U + Uni.U, "" + Glyph.WORD_UU);
        // UEUE
        mSuffixMap.put("" + Uni.UE + Uni.UE, "" + Glyph.WORD_UU);

        // Modal partical
        // DA
        mSuffixMap.put("" + Uni.DA + Uni.A, "" + Glyph.INIT_DA_FVS1 + Glyph.FINA_A);
        // DE
        mSuffixMap.put("" + Uni.DA + Uni.E, "" + Glyph.INIT_DA_FVS1 + Glyph.FINA_E);
    }

    public class Uni {
        public static final char ZWJ = '\u200d'; // Zero-width joiner
        public static final char NNBS = '\u202F'; // Narrow No-Break Space
        // Unicode Mongolian Values
        public static final char MONGOLIAN_BIRGA = '\u1800';
        public static final char MONGOLIAN_ELLIPSIS = '\u1801';
        public static final char MONGOLIAN_COMMA = '\u1802';
        public static final char MONGOLIAN_FULL_STOP = '\u1803';
        public static final char MONGOLIAN_COLON = '\u1804';
        public static final char MONGOLIAN_FOUR_DOTS = '\u1805';
        public static final char MONGOLIAN_NIRUGU = '\u180a';
        public static final char FVS1 = '\u180b';
        public static final char FVS2 = '\u180c';
        public static final char FVS3 = '\u180d';
        public static final char MVS = '\u180e'; // MONGOLIAN_VOWEL_SEPARATOR
        public static final char MONGOLIAN_DIGIT_ZERO = '\u1810';
        public static final char MONGOLIAN_DIGIT_ONE = '\u1811';
        public static final char MONGOLIAN_DIGIT_TWO = '\u1812';
        public static final char MONGOLIAN_DIGIT_THREE = '\u1813';
        public static final char MONGOLIAN_DIGIT_FOUR = '\u1814';
        public static final char MONGOLIAN_DIGIT_FIVE = '\u1815';
        public static final char MONGOLIAN_DIGIT_SIX = '\u1816';
        public static final char MONGOLIAN_DIGIT_SEVEN = '\u1817';
        public static final char MONGOLIAN_DIGIT_EIGHT = '\u1818';
        public static final char MONGOLIAN_DIGIT_NINE = '\u1819';
        public static final char A = '\u1820'; // MONGOLIAN_LETTER_xx
        public static final char E = '\u1821';
        public static final char I = '\u1822';
        public static final char O = '\u1823';
        public static final char U = '\u1824';
        public static final char OE = '\u1825';
        public static final char UE = '\u1826';
        public static final char EE = '\u1827';
        public static final char NA = '\u1828';
        public static final char ANG = '\u1829';
        public static final char BA = '\u182A';
        public static final char PA = '\u182B';
        public static final char QA = '\u182C';
        public static final char GA = '\u182D';
        public static final char MA = '\u182E';
        public static final char LA = '\u182F';
        public static final char SA = '\u1830';
        public static final char SHA = '\u1831';
        public static final char TA = '\u1832';
        public static final char DA = '\u1833';
        public static final char CHA = '\u1834';
        public static final char JA = '\u1835';
        public static final char YA = '\u1836';
        public static final char RA = '\u1837';
        public static final char WA = '\u1838';
        public static final char FA = '\u1839';
        public static final char KA = '\u183A';
        public static final char KHA = '\u183B';
        public static final char TSA = '\u183C';
        public static final char ZA = '\u183D';
        public static final char HAA = '\u183E';
        public static final char ZRA = '\u183F';
        public static final char LHA = '\u1840';
        public static final char ZHI = '\u1841';
        public static final char CHI = '\u1842';
    };

    // public static final char CURSOR_HOLDER = '\uE359'; // arbitrary unused char
    public static final char CURSOR_HOLDER = '|';

    private class Glyph {

        // Private Use Area glyph values
        private static final char NOTDEF = '\uE360';
        private static final char BIRGA = '\uE364';
        private static final char ELLIPSIS = '\uE365';
        private static final char COMMA = '\uE366';
        private static final char FULL_STOP = '\uE367';
        private static final char COLON = '\uE368';
        private static final char FOUR_DOTS = '\uE369';
        private static final char NIRUGU = '\uE36E';
        private static final char ZERO = '\uE374';
        private static final char ONE = '\uE375';
        private static final char TWO = '\uE376';
        private static final char THREE = '\uE377';
        private static final char FOUR = '\uE378';
        private static final char FIVE = '\uE379';
        private static final char SIX = '\uE37A';
        private static final char SEVEN = '\uE37B';
        private static final char EIGHT = '\uE37C';
        private static final char NINE = '\uE37D';
        private static final char QUESTION_EXCLAMATION = '\uE37E';
        private static final char EXCLAMATION_QUESTION = '\uE37F';
        private static final char ISOL_A = '\uE384';
        private static final char ISOL_A_FVS1 = '\uE385';
        private static final char INIT_A = '\uE386';
        private static final char MEDI_A = '\uE387';
        private static final char MEDI_A_FVS1 = '\uE388';
        private static final char FINA_A = '\uE389';
        private static final char FINA_A_FVS1 = '\uE38A';
        private static final char FINA_A_FVS2 = '\uE38B';
        private static final char ISOL_E = '\uE38C';
        private static final char ISOL_E_FVS1 = '\uE38D';
        private static final char INIT_E = '\uE38E';
        private static final char INIT_E_FVS1 = '\uE38F';
        private static final char MEDI_E = '\uE390';
        private static final char FINA_E = '\uE391';
        private static final char FINA_E_FVS1 = '\uE392';
        private static final char FINA_E_FVS2 = '\uE393';
        private static final char ISOL_I = '\uE394';
        private static final char ISOL_I_FVS1 = '\uE395';
        private static final char INIT_I = '\uE396';
        private static final char INIT_I_FVS1 = '\uE397';
        private static final char MEDI_I = '\uE398';
        private static final char MEDI_I_FVS1 = '\uE399';
        // TODO MEDI_I_FVS2 and MEDI_I_FVS3 have not been standardized in Unicode yet
        // Matching them to Baiti
        private static final char MEDI_I_FVS3 = '\uE39A';
        private static final char FINA_I = '\uE39B';
        private static final char ISOL_O = '\uE39C';
        private static final char ISOL_O_FVS1 = '\uE39D';
        private static final char INIT_O = '\uE39E';
        private static final char INIT_O_FVS1 = '\uE39F';
        private static final char MEDI_O = '\uE3A0';
        private static final char MEDI_O_FVS1 = '\uE3A1';
        private static final char FINA_O = '\uE3A2';
        private static final char FINA_O_FVS1 = '\uE3A3';
        private static final char ISOL_U = '\uE3A6';  // Using Init U gliph
        private static final char ISOL_U_FVS1 = '\uE3A4';
        private static final char ISOL_U_FVS2 = '\uE3A5';
        private static final char INIT_U = '\uE3A6';
        private static final char INIT_U_FVS1 = '\uE3A7';
        private static final char MEDI_U = '\uE3A8';
        private static final char MEDI_U_FVS1 = '\uE3A9';
        private static final char FINA_U = '\uE3AA';
        private static final char FINA_U_FVS1 = '\uE3AB';
        private static final char ISOL_OE = '\uE3AC';
        private static final char ISOL_OE_FVS1 = '\uE3AD';
        private static final char INIT_OE = '\uE3AE';
        private static final char MEDI_OE = '\uE3AF';
        private static final char MEDI_OE_FVS1 = '\uE3B0';
        private static final char MEDI_OE_FVS2 = '\uE3B1';
        private static final char FINA_OE = '\uE3B2';
        private static final char FINA_OE_FVS1 = '\uE3B3';
        private static final char ISOL_UE = '\uE3B6';
        private static final char ISOL_UE_FVS2 = '\uE3C3';
        private static final char ISOL_UE_FVS3 = '\uE3B5';
        private static final char INIT_UE = '\uE3B6';
        private static final char MEDI_UE = '\uE3B7';
        private static final char MEDI_UE_FVS1 = '\uE3B8';
        private static final char MEDI_UE_FVS2 = '\uE3B9';
        private static final char FINA_UE = '\uE3BA';
        private static final char FINA_UE_FVS1 = '\uE3BB';
        private static final char ISOL_EE = '\uE3BC';
        private static final char ISOL_EE_FVS1 = '\uE3BD';
        private static final char INIT_EE = '\uE3BE';
        private static final char INIT_EE_FVS1 = '\uE3BF';
        private static final char MEDI_EE = '\uE3C0';
        private static final char FINA_EE = '\uE3C1';
        private static final char INIT_UE_FVS1 = '\uE3C2';
        private static final char ISOL_UE_FVS1 = '\uE3B4';
        private static final char ISOL_NA = '\uE3C4';
        private static final char ISOL_NA_FVS1 = '\uE3C5';
        private static final char INIT_NA = '\uE3C6';
        private static final char INIT_NA_FVS1 = '\uE3C7';
        private static final char MEDI_NA = '\uE3C8';
        private static final char MEDI_NA_FVS1 = '\uE3C9';
        private static final char MEDI_NA_FVS2 = '\uE3C8'; // same as medial na
        private static final char FINA_NA = '\uE3CA';
        private static final char FINA_NA_FVS1 = '\uE3CB';
        private static final char ISOL_ANG = '\uE3CC';
        private static final char INIT_ANG = '\uE3CD';
        private static final char MEDI_ANG = '\uE3CE';
        private static final char FINA_ANG = '\uE3CF';
        private static final char MEDI_ANG_MEDI_QA = '\uE3D0';
        private static final char MEDI_ANG_MEDI_GA = '\uE3D1';
        private static final char MEDI_ANG_MEDI_MA = '\uE3D2';
        private static final char MEDI_ANG_MEDI_LA = '\uE3D3';
        private static final char ISOL_BA = '\uE3D4';
        private static final char INIT_BA = '\uE3D5';
        private static final char MEDI_BA = '\uE3D6';
        private static final char FINA_BA = '\uE3D7';
        private static final char INIT_BA_FINA_A = '\uE3D8';
        private static final char INIT_BA_MEDI_A = '\uE3D9';
        private static final char MEDI_BA_MEDI_A = '\uE3DA';
        private static final char MEDI_BA_FINA_A = '\uE3DB';
        private static final char INIT_BA_FINA_E = '\uE3DC';
        private static final char INIT_BA_MEDI_E = '\uE3DD';
        private static final char MEDI_BA_MEDI_E = '\uE3DE';
        private static final char MEDI_BA_FINA_E = '\uE3DF';
        private static final char INIT_BA_FINA_I = '\uE3E0';
        private static final char INIT_BA_MEDI_I = '\uE3E1';
        private static final char MEDI_BA_MEDI_I = '\uE3E2';
        private static final char MEDI_BA_FINA_I = '\uE3E3';
        private static final char INIT_BA_FINA_O = '\uE3E4';
        private static final char INIT_BA_MEDI_O = '\uE3E5';
        private static final char MEDI_BA_MEDI_O = '\uE3E6';
        private static final char MEDI_BA_FINA_O = '\uE3E7';
        private static final char INIT_BA_FINA_U = '\uE3E8';
        private static final char INIT_BA_MEDI_U = '\uE3E9';
        private static final char MEDI_BA_MEDI_U = '\uE3EA';
        private static final char MEDI_BA_FINA_U = '\uE3EB';
        private static final char INIT_BA_FINA_OE = '\uE3EC';
        private static final char INIT_BA_MEDI_OE = '\uE3ED';
        private static final char MEDI_BA_MEDI_OE = '\uE3EE';
        private static final char MEDI_BA_FINA_OE = '\uE3EF';
        private static final char MEDI_BA_FINA_OE_FVS1 = '\uE3F0';
        private static final char INIT_BA_FINA_UE = '\uE3F1';
        private static final char INIT_BA_MEDI_UE = '\uE3F2';
        private static final char MEDI_BA_MEDI_UE = '\uE3F3';
        private static final char MEDI_BA_FINA_UE = '\uE3F4';
        private static final char MEDI_BA_FINA_UE_FVS1 = '\uE3F5';
        private static final char INIT_BA_FINA_EE = '\uE3F6';
        private static final char INIT_BA_MEDI_EE = '\uE3F7';
        private static final char MEDI_BA_MEDI_EE = '\uE3F8';
        private static final char MEDI_BA_FINA_EE = '\uE3F9';
        private static final char MEDI_BA_MEDI_MA = '\uE3FA';
        private static final char MEDI_BA_MEDI_LA = '\uE3FB';
        private static final char ISOL_PA = '\uE3FC';
        private static final char INIT_PA = '\uE3FD';
        private static final char MEDI_PA = '\uE3FE';
        private static final char FINA_PA = '\uE3FF';
        private static final char INIT_PA_FINA_A = '\uE400';
        private static final char INIT_PA_MEDI_A = '\uE401';
        private static final char MEDI_PA_MEDI_A = '\uE402';
        private static final char MEDI_PA_FINA_A = '\uE403';
        private static final char INIT_PA_FINA_E = '\uE404';
        private static final char INIT_PA_MEDI_E = '\uE405';
        private static final char MEDI_PA_MEDI_E = '\uE406';
        private static final char MEDI_PA_FINA_E = '\uE407';
        private static final char INIT_PA_FINA_I = '\uE408';
        private static final char INIT_PA_MEDI_I = '\uE409';
        private static final char MEDI_PA_MEDI_I = '\uE40A';
        private static final char MEDI_PA_FINA_I = '\uE40B';
        private static final char INIT_PA_FINA_O = '\uE40C';
        private static final char INIT_PA_MEDI_O = '\uE40D';
        private static final char MEDI_PA_MEDI_O = '\uE40E';
        private static final char MEDI_PA_FINA_O = '\uE40F';
        private static final char INIT_PA_FINA_U = '\uE410';
        private static final char INIT_PA_MEDI_U = '\uE411';
        private static final char MEDI_PA_MEDI_U = '\uE412';
        private static final char MEDI_PA_FINA_U = '\uE413';
        private static final char INIT_PA_FINA_OE = '\uE414';
        private static final char INIT_PA_MEDI_OE = '\uE415';
        private static final char MEDI_PA_MEDI_OE = '\uE416';
        private static final char MEDI_PA_FINA_OE = '\uE417';
        private static final char MEDI_PA_FINA_OE_FVS1 = '\uE418';
        private static final char INIT_PA_FINA_UE = '\uE419';
        private static final char INIT_PA_MEDI_UE = '\uE41A';
        private static final char MEDI_PA_MEDI_UE = '\uE41B';
        private static final char MEDI_PA_FINA_UE = '\uE41C';
        private static final char MEDI_PA_FINA_UE_FVS1 = '\uE41D';
        private static final char INIT_PA_FINA_EE = '\uE41E';
        private static final char INIT_PA_MEDI_EE = '\uE41F';
        private static final char MEDI_PA_MEDI_EE = '\uE420';
        private static final char MEDI_PA_FINA_EE = '\uE421';
        private static final char MEDI_PA_MEDI_MA = '\uE422';
        private static final char MEDI_PA_MEDI_LA = '\uE423';
        private static final char ISOL_QA = '\uE424';
        private static final char ISOL_QA_FVS3 = '\uE425'; // TODO matching Baiti
        private static final char INIT_QA = '\uE426';
        private static final char INIT_QA_FVS1 = '\uE427';
        private static final char MEDI_QA = '\uE428';
        private static final char MEDI_QA_FVS1 = '\uE429';
        private static final char MEDI_QA_FVS2 = '\uE42A';
        private static final char FINA_QA = '\uE42B';
        private static final char FINA_QA_FVS1 = '\uE42C';
        private static final char FINA_QA_FVS2 = '\uE42D';
        private static final char INIT_QA_FINA_E = '\uE42E';
        private static final char INIT_QA_MEDI_E = '\uE42F';
        private static final char MEDI_QA_MEDI_E = '\uE430';
        private static final char MEDI_QA_FINA_E = '\uE431';
        private static final char INIT_QA_FINA_I = '\uE432';
        private static final char INIT_QA_MEDI_I = '\uE433';
        private static final char MEDI_QA_MEDI_I = '\uE434';
        private static final char MEDI_QA_FINA_I = '\uE435';
        private static final char INIT_QA_FINA_OE = '\uE436';
        private static final char INIT_QA_MEDI_OE = '\uE437';
        private static final char MEDI_QA_MEDI_OE = '\uE438';
        private static final char MEDI_QA_FINA_OE = '\uE439';
        private static final char MEDI_QA_FINA_OE_FVS1 = '\uE43A';
        private static final char INIT_QA_FINA_UE = '\uE43B';
        private static final char INIT_QA_MEDI_UE = '\uE43C';
        private static final char MEDI_QA_MEDI_UE = '\uE43D';
        private static final char MEDI_QA_FINA_UE = '\uE43E';
        private static final char MEDI_QA_FINA_UE_FVS1 = '\uE43F';
        private static final char INIT_QA_FINA_EE = '\uE440';
        private static final char INIT_QA_MEDI_EE = '\uE441';
        private static final char MEDI_QA_MEDI_EE = '\uE442';
        private static final char MEDI_QA_FINA_EE = '\uE443';
        private static final char ISOL_GA = '\uE446'; // TODO fix in iOS
        private static final char ISOL_GA_FVS3 = '\uE445'; // TODO not in Baiti
        private static final char INIT_GA = '\uE444'; // TODO fix in iOS (gap in initial masculine)
        private static final char INIT_GA_FVS1 = '\uE447';
        private static final char MEDI_GA = '\uE448';
        private static final char MEDI_GA_FVS1 = '\uE449';
        private static final char MEDI_GA_FVS2 = '\uE448'; // TODO matching Baiti, not using
        // \uE44A
        private static final char FINA_GA = '\uE44B';
        private static final char FINA_GA_FVS1 = '\uE44B'; // TODO matching Baiti
        private static final char FINA_GA_FVS3 = '\uE44C'; // TODO matching Baiti
        private static final char FINA_GA_FVS2 = '\uE44D';
        private static final char INIT_GA_FINA_E = '\uE44E';
        private static final char INIT_GA_MEDI_E = '\uE44F';
        private static final char MEDI_GA_MEDI_E = '\uE450';
        private static final char MEDI_GA_FINA_E = '\uE451';
        private static final char INIT_GA_FINA_I = '\uE452';
        private static final char INIT_GA_MEDI_I = '\uE453';
        private static final char MEDI_GA_MEDI_I = '\uE454';
        private static final char MEDI_GA_FINA_I = '\uE455';
        private static final char INIT_GA_FINA_OE = '\uE456';
        private static final char INIT_GA_MEDI_OE = '\uE457';
        private static final char MEDI_GA_MEDI_OE = '\uE458';
        private static final char MEDI_GA_FINA_OE = '\uE459';
        private static final char MEDI_GA_FINA_OE_FVS1 = '\uE45A';
        private static final char INIT_GA_FINA_UE = '\uE45B';
        private static final char INIT_GA_MEDI_UE = '\uE45C';
        private static final char MEDI_GA_MEDI_UE = '\uE45D';
        private static final char MEDI_GA_FINA_UE = '\uE45E';
        private static final char MEDI_GA_FINA_UE_FVS1 = '\uE45F';
        private static final char INIT_GA_FINA_EE = '\uE460';
        private static final char INIT_GA_MEDI_EE = '\uE461';
        private static final char MEDI_GA_MEDI_EE = '\uE462';
        private static final char MEDI_GA_FINA_EE = '\uE463';
        private static final char MEDI_GA_MEDI_MA = '\uE464';
        private static final char MEDI_GA_MEDI_LA = '\uE465';
        private static final char ISOL_MA = '\uE466';
        private static final char INIT_MA = '\uE467';
        private static final char MEDI_MA = '\uE468';
        private static final char FINA_MA = '\uE469';
        private static final char ISOL_LA = '\uE46A';
        private static final char INIT_LA = '\uE46B';
        private static final char MEDI_LA = '\uE46C';
        private static final char FINA_LA = '\uE46D';
        private static final char ISOL_SA = '\uE46E';
        private static final char INIT_SA = '\uE46F';
        private static final char MEDI_SA = '\uE470';
        private static final char FINA_SA = '\uE471';
        private static final char ISOL_SHA = '\uE472';
        private static final char INIT_SHA = '\uE473';
        private static final char MEDI_SHA = '\uE474';
        private static final char FINA_SHA = '\uE475';
        private static final char ISOL_TA = '\uE476';
        private static final char ISOL_TA_FVS1 = '\uE477';
        private static final char INIT_TA = '\uE478';
        private static final char MEDI_TA = '\uE479';
        private static final char MEDI_TA_FVS1 = '\uE47A';
        private static final char MEDI_TA_FVS2 = '\uE47B';
        private static final char FINA_TA = '\uE47C';
        private static final char ISOL_DA = '\uE47D';
        private static final char INIT_DA = '\uE47E';
        private static final char INIT_DA_FVS1 = '\uE47F';
        private static final char MEDI_DA = '\uE480';
        private static final char MEDI_DA_FVS1 = '\uE481';
        private static final char FINA_DA = '\uE482';
        private static final char FINA_DA_FVS1 = '\uE483';
        private static final char ISOL_CHA = '\uE484';
        private static final char INIT_CHA = '\uE485';
        private static final char MEDI_CHA = '\uE486';
        private static final char FINA_CHA = '\uE487';
        private static final char ISOL_JA = '\uE488';
        private static final char ISOL_JA_FVS1 = '\uE489';
        private static final char INIT_JA = '\uE48A';
        private static final char MEDI_JA = '\uE48B';
        private static final char FINA_JA = '\uE48C';
        private static final char FINA_JA_FVS1 = '\uE491'; // same as FINA_YA
        private static final char ISOL_YA = '\uE48D';
        private static final char INIT_YA = '\uE48E';
        private static final char INIT_YA_FVS1 = '\uE48F';
        private static final char MEDI_YA = '\uE398'; // same as MEDI_I
        private static final char MEDI_YA_FVS1 = '\uE490'; // TODO matching Baiti
        private static final char FINA_YA = '\uE491';
        private static final char ISOL_RA = '\uE492';
        private static final char INIT_RA = '\uE493';
        private static final char MEDI_RA = '\uE494';
        private static final char FINA_RA = '\uE495';
        private static final char ISOL_WA = '\uE496';
        private static final char INIT_WA = '\uE497';
        private static final char WORD_U = '\uE498';
        private static final char MEDI_WA_FVS1 = '\uE499'; // TODO matching Baiti
        private static final char FINA_WA_FVS1 = '\uE49A'; // TODO matching Baiti
        private static final char FINA_WA = '\uE49B'; // TODO matching Baiti
        private static final char ISOL_FA = '\uE49C';
        private static final char INIT_FA = '\uE49D';
        private static final char MEDI_FA = '\uE49E';
        private static final char FINA_FA = '\uE49F';
        private static final char INIT_FA_FINA_A = '\uE4A0';
        private static final char INIT_FA_MEDI_A = '\uE4A1';
        private static final char MEDI_FA_MEDI_A = '\uE4A2';
        private static final char MEDI_FA_FINA_A = '\uE4A3';
        private static final char INIT_FA_FINA_E = '\uE4A4';
        private static final char INIT_FA_MEDI_E = '\uE4A5';
        private static final char MEDI_FA_MEDI_E = '\uE4A6';
        private static final char MEDI_FA_FINA_E = '\uE4A7';
        private static final char INIT_FA_FINA_I = '\uE4A8';
        private static final char INIT_FA_MEDI_I = '\uE4A9';
        private static final char MEDI_FA_MEDI_I = '\uE4AA';
        private static final char MEDI_FA_FINA_I = '\uE4AB';
        private static final char INIT_FA_FINA_O = '\uE4AC';
        private static final char INIT_FA_MEDI_O = '\uE4AD';
        private static final char MEDI_FA_MEDI_O = '\uE4AE';
        private static final char MEDI_FA_FINA_O = '\uE4AF';
        private static final char INIT_FA_FINA_U = '\uE4B0';
        private static final char INIT_FA_MEDI_U = '\uE4B1';
        private static final char MEDI_FA_MEDI_U = '\uE4B2';
        private static final char MEDI_FA_FINA_U = '\uE4B3';
        private static final char INIT_FA_FINA_OE = '\uE4B4';
        private static final char INIT_FA_MEDI_OE = '\uE4B5';
        private static final char MEDI_FA_MEDI_OE = '\uE4B6';
        private static final char MEDI_FA_FINA_OE = '\uE4B7';
        private static final char MEDI_FA_FINA_OE_FVS1 = '\uE4B8';
        private static final char INIT_FA_FINA_UE = '\uE4B9';
        private static final char INIT_FA_MEDI_UE = '\uE4BA';
        private static final char MEDI_FA_MEDI_UE = '\uE4BB';
        private static final char MEDI_FA_FINA_UE = '\uE4BC';
        private static final char MEDI_FA_FINA_UE_FVS1 = '\uE4BD';
        private static final char INIT_FA_FINA_EE = '\uE4BE';
        private static final char INIT_FA_MEDI_EE = '\uE4BF';
        private static final char MEDI_FA_MEDI_EE = '\uE4C0';
        private static final char MEDI_FA_FINA_EE = '\uE4C1';
        private static final char MEDI_FA_MEDI_MA = '\uE4C2';
        private static final char MEDI_FA_MEDI_LA = '\uE4C3';
        private static final char ISOL_KA = '\uE4C4';
        private static final char INIT_KA = '\uE4C5';
        private static final char MEDI_KA = '\uE4C6';
        private static final char FINA_KA = '\uE4C7';
        private static final char INIT_KA_FINA_A = '\uE4C8';
        private static final char INIT_KA_MEDI_A = '\uE4C9';
        private static final char MEDI_KA_MEDI_A = '\uE4CA';
        private static final char MEDI_KA_FINA_A = '\uE4CB';
        private static final char INIT_KA_FINA_E = '\uE4CC';
        private static final char INIT_KA_MEDI_E = '\uE4CD';
        private static final char MEDI_KA_MEDI_E = '\uE4CE';
        private static final char MEDI_KA_FINA_E = '\uE4CF';
        private static final char INIT_KA_FINA_I = '\uE4D0';
        private static final char INIT_KA_MEDI_I = '\uE4D1';
        private static final char MEDI_KA_MEDI_I = '\uE4D2';
        private static final char MEDI_KA_FINA_I = '\uE4D3';
        private static final char INIT_KA_FINA_O = '\uE4D4';
        private static final char INIT_KA_MEDI_O = '\uE4D5';
        private static final char MEDI_KA_MEDI_O = '\uE4D6';
        private static final char MEDI_KA_FINA_O = '\uE4D7';
        private static final char INIT_KA_FINA_U = '\uE4D8';
        private static final char INIT_KA_MEDI_U = '\uE4D9';
        private static final char MEDI_KA_MEDI_U = '\uE4DA';
        private static final char MEDI_KA_FINA_U = '\uE4DB';
        private static final char INIT_KA_FINA_OE = '\uE4DC';
        private static final char INIT_KA_MEDI_OE = '\uE4DD';
        private static final char MEDI_KA_MEDI_OE = '\uE4DE';
        private static final char MEDI_KA_FINA_OE = '\uE4DF';
        private static final char MEDI_KA_FINA_OE_FVS1 = '\uE4E0';
        private static final char INIT_KA_FINA_UE = '\uE4E1';
        private static final char INIT_KA_MEDI_UE = '\uE4E2';
        private static final char MEDI_KA_MEDI_UE = '\uE4E3';
        private static final char MEDI_KA_FINA_UE = '\uE4E4';
        private static final char MEDI_KA_FINA_UE_FVS1 = '\uE4E5';
        private static final char INIT_KA_FINA_EE = '\uE4E6';
        private static final char INIT_KA_MEDI_EE = '\uE4E7';
        private static final char MEDI_KA_MEDI_EE = '\uE4E8';
        private static final char MEDI_KA_FINA_EE = '\uE4E9';
        private static final char MEDI_KA_MEDI_MA = '\uE4EA';
        private static final char MEDI_KA_MEDI_LA = '\uE4EB';
        private static final char ISOL_KHA = '\uE4EC';
        private static final char INIT_KHA = '\uE4ED';
        private static final char MEDI_KHA = '\uE4EE';
        private static final char FINA_KHA = '\uE4EF';
        private static final char INIT_KHA_FINA_A = '\uE4F0';
        private static final char INIT_KHA_MEDI_A = '\uE4F1';
        private static final char MEDI_KHA_MEDI_A = '\uE4F2';
        private static final char MEDI_KHA_FINA_A = '\uE4F3';
        private static final char INIT_KHA_FINA_E = '\uE4F4';
        private static final char INIT_KHA_MEDI_E = '\uE4F5';
        private static final char MEDI_KHA_MEDI_E = '\uE4F6';
        private static final char MEDI_KHA_FINA_E = '\uE4F7';
        private static final char INIT_KHA_FINA_I = '\uE4F8';
        private static final char INIT_KHA_MEDI_I = '\uE4F9';
        private static final char MEDI_KHA_MEDI_I = '\uE4FA';
        private static final char MEDI_KHA_FINA_I = '\uE4FB';
        private static final char INIT_KHA_FINA_O = '\uE4FC';
        private static final char INIT_KHA_MEDI_O = '\uE4FD';
        private static final char MEDI_KHA_MEDI_O = '\uE4FE';
        private static final char MEDI_KHA_FINA_O = '\uE4FF';
        private static final char INIT_KHA_FINA_U = '\uE500';
        private static final char INIT_KHA_MEDI_U = '\uE501';
        private static final char MEDI_KHA_MEDI_U = '\uE502';
        private static final char MEDI_KHA_FINA_U = '\uE503';
        private static final char INIT_KHA_FINA_OE = '\uE504';
        private static final char INIT_KHA_MEDI_OE = '\uE505';
        private static final char MEDI_KHA_MEDI_OE = '\uE506';
        private static final char MEDI_KHA_FINA_OE = '\uE507';
        private static final char MEDI_KHA_FINA_OE_FVS1 = '\uE508';
        private static final char INIT_KHA_FINA_UE = '\uE509';
        private static final char INIT_KHA_MEDI_UE = '\uE50A';
        private static final char MEDI_KHA_MEDI_UE = '\uE50B';
        private static final char MEDI_KHA_FINA_UE = '\uE50C';
        private static final char MEDI_KHA_FINA_UE_FVS1 = '\uE50D';
        private static final char INIT_KHA_FINA_EE = '\uE50E';
        private static final char INIT_KHA_MEDI_EE = '\uE50F';
        private static final char MEDI_KHA_MEDI_EE = '\uE510';
        private static final char MEDI_KHA_FINA_EE = '\uE511';
        private static final char MEDI_KHA_MEDI_MA = '\uE512';
        private static final char MEDI_KHA_MEDI_LA = '\uE513';
        private static final char ISOL_TSA = '\uE514';
        private static final char INIT_TSA = '\uE515';
        private static final char MEDI_TSA = '\uE516';
        private static final char FINA_TSA = '\uE517';
        private static final char ISOL_ZA = '\uE518';
        private static final char INIT_ZA = '\uE519';
        private static final char MEDI_ZA = '\uE51A';
        private static final char FINA_ZA = '\uE51B';
        private static final char ISOL_HAA = '\uE51C';
        private static final char INIT_HAA = '\uE51D';
        private static final char MEDI_HAA = '\uE51E';
        private static final char FINA_HAA = '\uE51F';
        private static final char ISOL_ZRA = '\uE520';
        private static final char INIT_ZRA = '\uE521';
        private static final char MEDI_ZRA = '\uE522';
        private static final char FINA_ZRA = '\uE523';
        private static final char ISOL_LHA = '\uE524';
        private static final char INIT_LHA = '\uE525';
        private static final char MEDI_LHA = '\uE526';
        private static final char FINA_LHA = '\uE527';
        private static final char ISOL_ZHI = '\uE528';
        private static final char INIT_ZHI = '\uE529';
        private static final char MEDI_ZHI = '\uE52A';
        private static final char FINA_ZHI = '\uE52B';
        private static final char ISOL_CHI = '\uE52C';
        private static final char INIT_CHI = '\uE52D';
        private static final char MEDI_CHI = '\uE52E';
        private static final char FINA_CHI = '\uE52F';
        private static final char FINA_SA_FVS1 = '\uE530';
        private static final char FINA_SA_FVS2 = '\uE531';
        private static final char FINA_BA_FVS1 = '\uE532';
        private static final char WORD_UU = '\uE533';
        private static final char WORD_BUU = '\uE534';
        private static final char MEDI_BA_MEDI_OE_FVS1 = '\uE535';
        private static final char MEDI_BA_MEDI_UE_FVS1 = '\uE536';
        private static final char MEDI_PA_MEDI_OE_FVS1 = '\uE537';
        private static final char MEDI_PA_MEDI_UE_FVS1 = '\uE538';
        private static final char MEDI_QA_MEDI_OE_FVS1 = '\uE539';
        private static final char MEDI_QA_MEDI_UE_FVS1 = '\uE53A';
        private static final char MEDI_GA_MEDI_OE_FVS1 = '\uE53B';
        private static final char MEDI_GA_MEDI_UE_FVS1 = '\uE53C';
        private static final char MEDI_FA_MEDI_OE_FVS1 = '\uE53D';
        private static final char MEDI_FA_MEDI_UE_FVS1 = '\uE53E';
        private static final char MEDI_KA_MEDI_OE_FVS1 = '\uE53F';
        private static final char MEDI_KA_MEDI_UE_FVS1 = '\uE540';
        private static final char MEDI_KHA_MEDI_OE_FVS1 = '\uE541';
        private static final char MEDI_KHA_MEDI_UE_FVS1 = '\uE542';
        private static final char MEDI_MA_MEDI_MA = '\uE544';
        private static final char MEDI_MA_MEDI_LA = '\uE545';
        private static final char MEDI_LA_MEDI_LA = '\uE546';
        private static final char MEDI_ANG_MEDI_NA_FVS1 = '\uE547';
        private static final char MEDI_ANG_FINA_QA = '\uE548';
        private static final char MEDI_ANG_FINA_GA = '\uE549';
        private static final char MEDI_BA_MEDI_QA = '\uE54A';
        private static final char MEDI_BA_MEDI_GA = '\uE54B';
        private static final char MEDI_PA_MEDI_QA = '\uE54C';
        private static final char MEDI_PA_MEDI_GA = '\uE54D';
        private static final char MEDI_FA_MEDI_QA = '\uE54E';
        private static final char MEDI_FA_MEDI_GA = '\uE54F';
        private static final char MEDI_KA_MEDI_QA = '\uE550';
        private static final char MEDI_KA_MEDI_GA = '\uE551';
        private static final char MEDI_KHA_MEDI_QA = '\uE552';
        private static final char MEDI_KHA_MEDI_GA = '\uE553';
        private static final char MEDI_BA_MEDI_NA_FVS1 = '\uE554';
        private static final char MEDI_PA_MEDI_NA_FVS1 = '\uE555';
        private static final char MEDI_GA_MEDI_NA_FVS1 = '\uE556';
        private static final char MEDI_FA_MEDI_NA_FVS1 = '\uE557';
        private static final char MEDI_KA_MEDI_NA_FVS1 = '\uE558';
        private static final char MEDI_KHA_MEDI_NA_FVS1 = '\uE559';
        private static final char INIT_QA_FINA_OE_FVS1 = '\uE55A';
        private static final char INIT_QA_FINA_UE_FVS1 = '\uE55B';
        private static final char INIT_GA_FINA_OE_FVS1 = '\uE55C';
        private static final char INIT_GA_FINA_UE_FVS1 = '\uE55D';
        private static final char INIT_QA_MEDI_OE_FVS1 = '\uE55E';
        private static final char INIT_QA_MEDI_UE_FVS1 = '\uE55F';
        private static final char INIT_GA_MEDI_OE_FVS1 = '\uE560';
        private static final char INIT_GA_MEDI_UE_FVS1 = '\uE561';
        private static final char ISOL_QA_FVS2 = '\uE564';
        private static final char INIT_QA_FVS2 = '\uE565';
        private static final char ISOL_QA_FVS1 = '\uE566'; // TODO matching Baiti
        private static final char INIT_QA_FVS3 = '\uE567';
        private static final char MEDI_QA_FVS3 = '\uE568';
        private static final char INIT_QA_FVS1_FINA_E = '\uE569';
        private static final char INIT_QA_FVS1_MEDI_E = '\uE56A';
        private static final char MEDI_QA_FVS1_MEDI_E = '\uE56B';
        private static final char MEDI_QA_FVS1_FINA_E = '\uE56C';
        private static final char INIT_QA_FVS1_FINA_I = '\uE56D';
        private static final char INIT_QA_FVS1_MEDI_I = '\uE56E';
        private static final char MEDI_QA_FVS1_MEDI_I = '\uE56F';
        private static final char MEDI_QA_FVS1_FINA_I = '\uE570';
        private static final char INIT_QA_FVS1_FINA_OE = '\uE571';
        private static final char INIT_QA_FVS1_MEDI_OE = '\uE572';
        private static final char MEDI_QA_FVS1_MEDI_OE = '\uE573';
        private static final char MEDI_QA_FVS1_FINA_OE = '\uE574';
        private static final char MEDI_QA_FVS1_FINA_OE_FVS1 = '\uE575';
        private static final char INIT_QA_FVS1_FINA_UE = '\uE576';
        private static final char INIT_QA_FVS1_MEDI_UE = '\uE577';
        private static final char MEDI_QA_FVS1_MEDI_UE = '\uE578';
        private static final char MEDI_QA_FVS1_FINA_UE = '\uE579';
        private static final char MEDI_QA_FVS1_FINA_UE_FVS1 = '\uE57A';
        private static final char INIT_QA_FVS1_FINA_EE = '\uE57B';
        private static final char INIT_QA_FVS1_MEDI_EE = '\uE57C';
        private static final char MEDI_QA_FVS1_MEDI_EE = '\uE57D';
        private static final char MEDI_QA_FVS1_FINA_EE = '\uE57E';
        private static final char ISOL_GA_FVS1 = '\uE57F'; // TODO matching Baiti
        private static final char ISOL_GA_FVS2 = '\uE580'; // TODO matching Baiti
        private static final char INIT_GA_FVS3 = '\uE581'; // TODO not in Baiti
        private static final char INIT_GA_FVS2 = '\uE582'; // TODO matching Baiti
        private static final char MEDI_GA_FVS3 = '\uE583';
        private static final char MEDI_WA = '\uE584'; // TODO matching Baiti
        private static final char INIT_A_FVS1 = '\uE585';
        // TODO MEDI_I_FVS2 and MEDI_I_FVS3 have not been standardized in Unicode yet
        // Matching to Baiti
        private static final char MEDI_I_FVS2 = '\uE586';
        private static final char FINA_NA_FVS2 = '\uE587';
        private static final char BIRGA_1 = '\uE588';
        private static final char BIRGA_2 = '\uE589';
        private static final char BIRGA_3 = '\uE58A';
        private static final char BIRGA_4 = '\uE58B';
        private static final char NIRUGU_FVS2 = '\uE58F';
        private static final char NIRUGU_FVS3 = '\uE590';
        private static final char INIT_GA_FVS1_FINA_E = '\uE594';
        private static final char INIT_GA_FVS1_MEDI_E = '\uE595';
        private static final char MEDI_GA_FVS1_MEDI_E = '\uE596';
        private static final char MEDI_GA_FVS1_FINA_E = '\uE597';
        private static final char INIT_GA_FVS1_FINA_I = '\uE598';
        private static final char INIT_GA_FVS1_MEDI_I = '\uE599';
        private static final char MEDI_GA_FVS1_MEDI_I = '\uE59A';
        private static final char MEDI_GA_FVS1_FINA_I = '\uE59B';
        private static final char INIT_GA_FVS1_FINA_OE = '\uE59C';
        private static final char INIT_GA_FVS1_MEDI_OE = '\uE59D';
        private static final char MEDI_GA_FVS1_MEDI_OE = '\uE59E';
        private static final char MEDI_GA_FVS1_FINA_OE = '\uE59F';
        private static final char MEDI_GA_FVS1_FINA_OE_FVS1 = '\uE5A0';
        private static final char INIT_GA_FVS1_FINA_UE = '\uE5A1';
        private static final char INIT_GA_FVS1_MEDI_UE = '\uE5A2';
        private static final char MEDI_GA_FVS1_MEDI_UE = '\uE5A3';
        private static final char MEDI_GA_FVS1_FINA_UE = '\uE5A4';
        private static final char MEDI_GA_FVS1_FINA_UE_FVS1 = '\uE5A5';
        private static final char INIT_GA_FVS1_FINA_EE = '\uE5A6';
        private static final char INIT_GA_FVS1_MEDI_EE = '\uE5A7';
        private static final char MEDI_GA_FVS1_MEDI_EE = '\uE5A8';
        private static final char MEDI_GA_FVS1_FINA_EE = '\uE5A9';
        private static final char MEDI_QA_FVS1_MEDI_OE_FVS1 = '\uE5AA';
        private static final char MEDI_QA_FVS1_MEDI_UE_FVS1 = '\uE5AB';
        private static final char MEDI_GA_FVS1_MEDI_OE_FVS1 = '\uE5AC';
        private static final char MEDI_GA_FVS1_MEDI_UE_FVS1 = '\uE5AD';
        private static final char INIT_BA_FINA_OE_FVS1 = '\uE5B4';
        private static final char INIT_BA_FINA_UE_FVS1 = '\uE5B5';
        private static final char INIT_BA_MEDI_OE_FVS1 = '\uE5B6';
        private static final char INIT_BA_MEDI_UE_FVS1 = '\uE5B7';
        private static final char INIT_PA_FINA_OE_FVS1 = '\uE5B8';
        private static final char INIT_PA_FINA_UE_FVS1 = '\uE5B9';
        private static final char INIT_PA_MEDI_OE_FVS1 = '\uE5BA';
        private static final char INIT_PA_MEDI_UE_FVS1 = '\uE5BB';
        private static final char INIT_QA_FVS1_FINA_OE_FVS1 = '\uE5BC';
        private static final char INIT_QA_FVS1_FINA_UE_FVS1 = '\uE5BD';
        private static final char INIT_QA_FVS1_MEDI_OE_FVS1 = '\uE5BE';
        private static final char INIT_QA_FVS1_MEDI_UE_FVS1 = '\uE5BF';
        private static final char INIT_GA_FVS1_FINA_OE_FVS1 = '\uE5C0';
        private static final char INIT_GA_FVS1_FINA_UE_FVS1 = '\uE5C1';
        private static final char INIT_GA_FVS1_MEDI_OE_FVS1 = '\uE5C2';
        private static final char INIT_GA_FVS1_MEDI_UE_FVS1 = '\uE5C3';
        private static final char INIT_FA_FINA_OE_FVS1 = '\uE5C4';
        private static final char INIT_FA_FINA_UE_FVS1 = '\uE5C5';
        private static final char INIT_FA_MEDI_OE_FVS1 = '\uE5C6';
        private static final char INIT_FA_MEDI_UE_FVS1 = '\uE5C7';
        private static final char INIT_KA_FINA_OE_FVS1 = '\uE5C8';
        private static final char INIT_KA_FINA_UE_FVS1 = '\uE5C9';
        private static final char INIT_KA_MEDI_OE_FVS1 = '\uE5CA';
        private static final char INIT_KA_MEDI_UE_FVS1 = '\uE5CB';
        private static final char INIT_KHA_FINA_OE_FVS1 = '\uE5CC';
        private static final char INIT_KHA_FINA_UE_FVS1 = '\uE5CD';
        private static final char INIT_KHA_MEDI_OE_FVS1 = '\uE5CE';
        private static final char INIT_KHA_MEDI_UE_FVS1 = '\uE5CF';
    }



}
