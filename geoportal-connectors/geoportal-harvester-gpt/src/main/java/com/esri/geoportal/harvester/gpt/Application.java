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
package com.esri.geoportal.harvester.gpt;

import com.esri.geoportal.harvester.api.defs.EntityDefinition;
import com.esri.geoportal.harvester.api.DataReference;
import com.esri.geoportal.commons.utils.SimpleCredentials;
import com.esri.geoportal.harvester.api.base.DataReferenceSerializer;
import java.net.URL;

/**
 * Application.
 */
public class Application {
  public static void main(String[] args) throws Exception {
    if (args.length==3) {
      URL url = new URL(args[0]+(args[0].endsWith("/")? "": "/"));
      String userName = args[1];
      String password = args[2];
      GptConnector connector = new GptConnector();
      EntityDefinition def = new EntityDefinition();
      GptBrokerDefinitionAdaptor adaptor = new GptBrokerDefinitionAdaptor(def);
      adaptor.setHostUrl(url);
      adaptor.setCredentials(new SimpleCredentials(userName, password));
      
      GptBroker broker = connector.createBroker(def);
      DataReferenceSerializer ser = new DataReferenceSerializer();
      DataReference ref = null;
      while (( ref = ser.deserialize(System.in))!=null) {
        System.out.println(String.format("publishing: %s", ref.getId()));
        broker.publish(ref);
      }
    }
  }
}
