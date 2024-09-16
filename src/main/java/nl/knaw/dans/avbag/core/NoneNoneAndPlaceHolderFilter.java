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
package nl.knaw.dans.avbag.core;

import lombok.AllArgsConstructor;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

@AllArgsConstructor
public class NoneNoneAndPlaceHolderFilter implements Predicate<Element> {
    private final PlaceHolders placeHolders;

    @Override
    public boolean test(Element element) {
        return placeHolders.getPaths().contains(Paths.get(element.getAttribute("filepath"))) || (
            XmlUtil.isAccessibleToNone(element) && XmlUtil.isVisibleToNone(element)
        );
    }
}
