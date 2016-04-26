/*
 * Copyright 2016 Esri, Inc..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.esri.geoportal.harvester.beans;

import com.esri.geoportal.harvester.folder.FolderPublisherFactory;
import com.esri.geoportal.harvester.gpt.GptPublisherFactory;
import com.esri.geoportal.harvester.engine.support.DataDestinationRegistry;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;

/**
 * Data destination registry bean.
 */
@Service
public class DataDestinationRegistryBean extends DataDestinationRegistry {
  
  @PostConstruct
  public void init() {
    put("FOLDER", new FolderPublisherFactory());
    put("GPT", new GptPublisherFactory());
  }
}
