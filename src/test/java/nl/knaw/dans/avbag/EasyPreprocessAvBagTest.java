/*
 * Copyright (C) 2024 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.avbag;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static java.util.Collections.singletonList;
import static nl.knaw.dans.lib.util.AbstractCommandLineApp.CONFIG_FILE_KEY;
import static nl.knaw.dans.lib.util.AbstractCommandLineApp.EXAMPLE_CONFIG_FILE_KEY;
import static org.assertj.core.api.Assertions.assertThat;

public class EasyPreprocessAvBagTest extends AbstractTestWithTestDir {

    @Disabled("This test serves debugging, the main method of EasyPreprocessAvBag would abort when executed in a suite")
    @Test
    public void testHelp() throws Exception {
        ByteArrayOutputStream out = TestUtils.captureStdout();
        String[] args = singletonList("--help").toArray(new String[0]);
        System.setProperty(CONFIG_FILE_KEY, testDir.resolve("does-not-exit.yml").toString());
        System.setProperty(EXAMPLE_CONFIG_FILE_KEY, "src/main/assembly/dist/cfg/example-config.yml");
        EasyPreprocessAvBag.main(args);
    }
}