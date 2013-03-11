/*
 * NOTE: Copied from Netty 3.6.3 to improve play 1.2 CookieDecoding
 *       in order to avoid upgrading Netty.
 * =================================================================
 *
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package play.utils;
import org.jboss.netty.handler.codec.http.*;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

// package org.jboss.netty.handler.codec.http;

// import org.jboss.netty.util.internal.StringUtil;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decodes an HTTP header value into {@link Cookie}s.  This decoder can decode
 * the HTTP cookie version 0, 1, and 2.
 *
 * <pre>
 * {@link HttpRequest} req = ...;
 * String value = req.getHeader("Cookie");
 * Set&lt;{@link Cookie}&gt; cookies = new {@link CookieDecoder}().decode(value);
 * </pre>
 *
 * @see CookieEncoder
 *
 * @apiviz.stereotype utility
 * @apiviz.has        org.jboss.netty.handler.codec.http.Cookie oneway - - decodes
 */
public class CookieDecoder {

    private static final char COMMA = ',';

    /**
     * Creates a new decoder.
     */
    public CookieDecoder() {
    }

    /**
     * @deprecated Use {@link #CookieDecoder()} instead.
     */
    @Deprecated
    public CookieDecoder(@SuppressWarnings("unused") boolean lenient) {
    }

    /**
     * Decodes the specified HTTP header value into {@link Cookie}s.
     *
     * @return the decoded {@link Cookie}s
     */
    public Set<Cookie> decode(String header) {
        List<String> names = new ArrayList<String>(8);
        List<String> values = new ArrayList<String>(8);
        extractKeyValuePairs(header, names, values);

        if (names.isEmpty()) {
            return Collections.emptySet();
        }

        int i;
        int version = 0;

        // $Version is the only attribute that can appear before the actual
        // cookie name-value pair.
        if (names.get(0).equalsIgnoreCase(CookieHeaderNames.VERSION)) {
            try {
                version = Integer.parseInt(values.get(0));
            } catch (NumberFormatException e) {
                // Ignore.
            }
            i = 1;
        } else {
            i = 0;
        }

        if (names.size() <= i) {
            // There's a version attribute, but nothing more.
            return Collections.emptySet();
        }

        Set<Cookie> cookies = new TreeSet<Cookie>();
        for (; i < names.size(); i ++) {
            String name = names.get(i);
            String value = values.get(i);
            if (value == null) {
                value = "";
            }

            Cookie c = new DefaultCookie(name, value);

            boolean discard = false;
            boolean secure = false;
            boolean httpOnly = false;
            String comment = null;
            String commentURL = null;
            String domain = null;
            String path = null;
            // int maxAge = Integer.MIN_VALUE;
            int maxAge = -1;//looks like older netty needed -1 here, so mimicing that behavior
            List<Integer> ports = new ArrayList<Integer>(2);

            for (int j = i + 1; j < names.size(); j++, i++) {
                name = names.get(j);
                value = values.get(j);

                if (CookieHeaderNames.DISCARD.equalsIgnoreCase(name)) {
                    discard = true;
                } else if (CookieHeaderNames.SECURE.equalsIgnoreCase(name)) {
                    secure = true;
                } else if (CookieHeaderNames.HTTPONLY.equalsIgnoreCase(name)) {
                   httpOnly = true;
                } else if (CookieHeaderNames.COMMENT.equalsIgnoreCase(name)) {
                    comment = value;
                } else if (CookieHeaderNames.COMMENTURL.equalsIgnoreCase(name)) {
                    commentURL = value;
                } else if (CookieHeaderNames.DOMAIN.equalsIgnoreCase(name)) {
                    domain = value;
                } else if (CookieHeaderNames.PATH.equalsIgnoreCase(name)) {
                    path = value;
                } else if (CookieHeaderNames.EXPIRES.equalsIgnoreCase(name)) {
                    try {
                        long maxAgeMillis =
                            new CookieDateFormat().parse(value).getTime() -
                            System.currentTimeMillis();

                        maxAge = (int) (maxAgeMillis / 1000) +
                                 (maxAgeMillis % 1000 != 0? 1 : 0);

                    } catch (ParseException e) {
                        // Ignore.
                    }
                } else if (CookieHeaderNames.MAX_AGE.equalsIgnoreCase(name)) {
                    maxAge = Integer.parseInt(value);
                } else if (CookieHeaderNames.VERSION.equalsIgnoreCase(name)) {
                    version = Integer.parseInt(value);
                } else if (CookieHeaderNames.PORT.equalsIgnoreCase(name)) {
                    String[] portList = NettyStringUtil.split(value, COMMA);
                    for (String s1: portList) {
                        try {
                            ports.add(Integer.valueOf(s1));
                        } catch (NumberFormatException e) {
                            // Ignore.
                        }
                    }
                } else {
                    break;
                }
            }

            c.setVersion(version);
            c.setMaxAge(maxAge);
            c.setPath(path);
            c.setDomain(domain);
            c.setSecure(secure);
            c.setHttpOnly(httpOnly);
            if (version > 0) {
                c.setComment(comment);
            }
            if (version > 1) {
                c.setCommentUrl(commentURL);
                c.setPorts(ports);
                c.setDiscard(discard);
            }

            cookies.add(c);
        }

        return cookies;
    }

    private static void extractKeyValuePairs(
            final String header, final List<String> names, final List<String> values) {

        final int headerLen  = header.length();
        loop: for (int i = 0;;) {

            // Skip spaces and separators.
            for (;;) {
                if (i == headerLen) {
                    break loop;
                }
                switch (header.charAt(i)) {
                case '\t': case '\n': case 0x0b: case '\f': case '\r':
                case ' ':  case ',':  case ';':
                    i ++;
                    continue;
                }
                break;
            }

            // Skip '$'.
            for (;;) {
                if (i == headerLen) {
                    break loop;
                }
                if (header.charAt(i) == '$') {
                    i ++;
                    continue;
                }
                break;
            }

            String name;
            String value;

            if (i == headerLen) {
                name = null;
                value = null;
            } else {
                int newNameStart = i;
                keyValLoop: for (;;) {
                    switch (header.charAt(i)) {
                    case ';':
                        // NAME; (no value till ';')
                        name = header.substring(newNameStart, i);
                        value = null;
                        break keyValLoop;
                    case '=':
                        // NAME=VALUE
                        name = header.substring(newNameStart, i);
                        i ++;
                        if (i == headerLen) {
                            // NAME= (empty value, i.e. nothing after '=')
                            value = "";
                            break keyValLoop;
                        }

                        int newValueStart = i;
                        char c = header.charAt(i);
                        if (c == '"' || c == '\'') {
                            // NAME="VALUE" or NAME='VALUE'
                            StringBuilder newValueBuf = new StringBuilder(header.length() - i);
                            final char q = c;
                            boolean hadBackslash = false;
                            i ++;
                            for (;;) {
                                if (i == headerLen) {
                                    value = newValueBuf.toString();
                                    break keyValLoop;
                                }
                                if (hadBackslash) {
                                    hadBackslash = false;
                                    c = header.charAt(i ++);
                                    switch (c) {
                                    case '\\': case '"': case '\'':
                                        // Escape last backslash.
                                        newValueBuf.setCharAt(newValueBuf.length() - 1, c);
                                        break;
                                    default:
                                        // Do not escape last backslash.
                                        newValueBuf.append(c);
                                    }
                                } else {
                                    c = header.charAt(i ++);
                                    if (c == q) {
                                        value = newValueBuf.toString();
                                        break keyValLoop;
                                    }
                                    newValueBuf.append(c);
                                    if (c == '\\') {
                                        hadBackslash = true;
                                    }
                                }
                            }
                        } else {
                            // NAME=VALUE;
                            int semiPos = header.indexOf(';', i);
                            if (semiPos > 0) {
                                value = header.substring(newValueStart, semiPos);
                                i = semiPos;
                            } else {
                                value = header.substring(newValueStart);
                                i = headerLen;
                            }
                        }
                        break keyValLoop;
                    default:
                        i ++;
                    }

                    if (i == headerLen) {
                        // NAME (no value till the end of string)
                        name = header.substring(newNameStart);
                        value = null;
                        break;
                    }
                }
            }

            names.add(name);
            values.add(value);
        }
    }
}


/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
final class CookieHeaderNames {
    static final String PATH = "Path";

    static final String EXPIRES = "Expires";

    static final String MAX_AGE = "Max-Age";

    static final String DOMAIN = "Domain";

    static final String SECURE = "Secure";

    static final String HTTPONLY = "HTTPOnly";

    static final String COMMENT = "Comment";

    static final String COMMENTURL = "CommentURL";

    static final String DISCARD = "Discard";

    static final String PORT = "Port";

    static final String VERSION = "Version";

    private CookieHeaderNames() {
        // Unused.
    }
}

/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/*
package org.jboss.netty.handler.codec.http;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
*/

final class CookieDateFormat extends SimpleDateFormat {

    private static final long serialVersionUID = 1789486337887402640L;

    CookieDateFormat() {
        super("E, d-MMM-y HH:mm:ss z", Locale.ENGLISH);
        setTimeZone(TimeZone.getTimeZone("GMT"));
    }
}

/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/*
package org.jboss.netty.util.internal;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
*/

/**
 * String utility class.
 * NOTE: WAS StringUtil in Netty src
 */
final class NettyStringUtil {

    private NettyStringUtil() {
        // Unused.
    }

    public static final String NEWLINE;

    static {
        String newLine;

        try {
            newLine = new Formatter().format("%n").toString();
        } catch (Exception e) {
            newLine = "\n";
        }

        NEWLINE = newLine;
    }

    /**
     * Strip an Object of it's ISO control characters.
     *
     * @param value
     *          The Object that should be stripped. This objects toString method will
     *          called and the result passed to {@link #stripControlCharacters(String)}.
     * @return {@code String}
     *          A new String instance with its hexadecimal control characters replaced
     *          by a space. Or the unmodified String if it does not contain any ISO
     *          control characters.
     */
    public static String stripControlCharacters(Object value) {
        if (value == null) {
            return null;
        }

        return stripControlCharacters(value.toString());
    }

    /**
     * Strip a String of it's ISO control characters.
     *
     * @param value
     *          The String that should be stripped.
     * @return {@code String}
     *          A new String instance with its hexadecimal control characters replaced
     *          by a space. Or the unmodified String if it does not contain any ISO
     *          control characters.
     */
    public static String stripControlCharacters(String value) {
        if (value == null) {
            return null;
        }

        boolean hasControlChars = false;
        for (int i = value.length() - 1; i >= 0; i --) {
            if (Character.isISOControl(value.charAt(i))) {
                hasControlChars = true;
                break;
            }
        }

        if (!hasControlChars) {
            return value;
        }

        StringBuilder buf = new StringBuilder(value.length());
        int i = 0;

        // Skip initial control characters (i.e. left trim)
        for (; i < value.length(); i ++) {
            if (!Character.isISOControl(value.charAt(i))) {
                break;
            }
        }

        // Copy non control characters and substitute control characters with
        // a space.  The last control characters are trimmed.
        boolean suppressingControlChars = false;
        for (; i < value.length(); i ++) {
            if (Character.isISOControl(value.charAt(i))) {
                suppressingControlChars = true;
                continue;
            } else {
                if (suppressingControlChars) {
                    suppressingControlChars = false;
                    buf.append(' ');
                }
                buf.append(value.charAt(i));
            }
        }

        return buf.toString();
    }

    private static final String EMPTY_STRING = "";

    /**
     * Splits the specified {@link String} with the specified delimiter.  This operation is a simplified and optimized
     * version of {@link String#split(String)}.
     */
    public static String[] split(String value, char delim) {
        final int end = value.length();
        final List<String> res = new ArrayList<String>();

        int start = 0;
        for (int i = 0; i < end; i ++) {
            if (value.charAt(i) == delim) {
                if (start == i) {
                    res.add(EMPTY_STRING);
                } else {
                    res.add(value.substring(start, i));
                }
                start = i + 1;
            }
        }

        if (start == 0) { // If no delimiter was found in the value
            res.add(value);
        } else {
            if (start != end) {
                // Add the last element if it's not empty.
                res.add(value.substring(start, end));
            } else {
                // Truncate trailing empty elements.
                for (int i = res.size() - 1; i >= 0; i --) {
                    if (res.get(i).length() == 0) {
                        res.remove(i);
                    } else {
                        break;
                    }
                }
            }
        }

        return res.toArray(new String[res.size()]);
    }
}

