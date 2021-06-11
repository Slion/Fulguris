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

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import jp.hazuki.yuzubrowser.adblock.repository.original.AdBlock;

public class AdBlockDecoder {

    public static List<AdBlock> decode(String text, boolean comment) {
        return decode(new Scanner(text), comment);
    }

    public static List<AdBlock> decode(Scanner scanner, boolean comment) {
        List<AdBlock> adBlocks = new ArrayList<>();
        while (scanner.hasNext()) {
            String line = scanner.nextLine().trim();
            if (!TextUtils.isEmpty(line)) {
                if (comment && (line.charAt(0) == '#' || line.startsWith("//")))
                    continue;

                if (line.startsWith("127.0.0.1"))
                    line = line.replace("127.0.0.1", "h");
                else if (line.startsWith("0.0.0.0"))
                    line = line.replace("0.0.0.0", "h");

                adBlocks.add(new AdBlock(line));
            }
        }
        return adBlocks;
    }
}
