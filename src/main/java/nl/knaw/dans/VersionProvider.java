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
package nl.knaw.dans;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
public class VersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
        // this returns null when executed locally with start.sh (mvn exec:java)
        Package p = this.getClass().getPackage();
        String implementationVersion = p.getImplementationVersion();
        return new String[] { implementationVersion };
    }
}