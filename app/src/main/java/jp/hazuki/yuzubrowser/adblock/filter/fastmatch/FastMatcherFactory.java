/*
 * Copyright (C) 2017-2019 Hazuki
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

package jp.hazuki.yuzubrowser.adblock.filter.fastmatch;

import java.nio.CharBuffer;

public final class FastMatcherFactory {

    private CharBuffer mainBuffer;

    private CharBuffer getBuffer(int length) {
        if (mainBuffer == null || mainBuffer.capacity() < length * 2)
            mainBuffer = CharBuffer.allocate(length * 2);
        mainBuffer.clear();
        return mainBuffer;
    }

    public void release() {
        mainBuffer = null;
    }

    public String fastCompile(String item) {
        return fastCompile(getBuffer(item.length()), item);
    }

    private String fastCompile(CharBuffer cb, String item) {
        boolean escape = false;
        for (int i = 0; item.length() > i; i++) {
            char c = item.charAt(i);
            switch (c) {
                case '#':
                    if (escape) {
                        cb.reset();
                        cb.put('#');
                    } else {
                        cb.put('\\').put('d');
                    }
                    break;
                case '?':
                    if (escape) {
                        cb.reset();
                        cb.put('?');
                    } else {
                        cb.put('.');
                    }
                    break;
                case '*':
                    if (escape) {
                        cb.reset();
                        cb.put('*');
                    } else {
                        cb.put('.').put('*');
                    }
                    break;
                case '+':
                    if (escape) {
                        cb.reset();
                        cb.put('+');
                    } else {
                        cb.put('.').put('+');
                    }
                    break;
                case '.':
                    cb.put('\\').put('.');
                    break;
                case '\\':
                    escape = true;
                    cb.put('\\');
                    cb.mark();
                    cb.put('\\');
                    continue;
                case '^':
                    cb.put('\\').put('^');
                    break;
                case '$':
                    cb.put('\\').put('$');
                    break;
                case '|':
                    cb.put('\\').put('|');
                    break;
                case '(':
                    cb.put('\\').put('(');
                    break;
                case ')':
                    cb.put('\\').put(')');
                    break;
                case '{':
                    cb.put('\\').put('{');
                    break;
                case '}':
                    cb.put('\\').put('}');
                    break;
                case '[':
                    cb.put('\\').put('[');
                    break;
                case ']':
                    cb.put('\\').put(']');
                    break;
                default:
                    cb.put(c);
                    break;
            }
            escape = false;
        }
        cb.flip();
        return cb.toString();
    }
}
