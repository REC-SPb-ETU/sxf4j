/*
 * Copyright 2017 iserge.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cleanlogic.sxf4j.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * Supported text encodings by SXF format
 * ASCIIZ - IBM866
 * ANSI - CP1251
 * KOI8-r - KOI8R
 * @author Serge Silaev aka iSergio <s.serge.b@gmail.com>
 */
public enum TextEncoding {
    /**
     * В кодировке ASCIIZ(DOS)
     */
    IBM866,
    /**
     * В кодировке ANSI
     */
    CP1251,
    /**
     * В кодировке KOI8-R(Unix)
     */
    KOI8R;

    /**
     * Map contains all enums names. Its will be used in ComboBox's and etc.
     */
    private static final Map<TextEncoding, String> _names = new HashMap<>();
    static {
        _names.put(IBM866, String.valueOf(IBM866));
        _names.put(CP1251, String.valueOf(CP1251));
        _names.put(KOI8R, "KOI8-R");
    }

    /**
     * Converts from integer value into enum.
     * @param value integer value of enum.
     * @return enum by integer.
     */
    public static TextEncoding fromValue(int value) {
        return values()[value];
    }

    /**
     * Get name of enum.
     * @return name of enum.
     */
    public String getName() {
        return _names.get(this);
    }

    /**
     * Get list of name of all enums.
     * @return name list of all enums.
     */
    public static Map<TextEncoding, String> getNames() {
        return _names;
    }

    /**
     * Get name of enum
     * @param textEncoding enum
     * @return name
     */
    public static String getName(TextEncoding textEncoding) {
        return _names.get(textEncoding);
    }
}
