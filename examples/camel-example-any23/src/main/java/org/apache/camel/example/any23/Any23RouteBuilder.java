/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.example.any23;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.Any23Type;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class Any23RouteBuilder extends RouteBuilder {

  @Override
  public void configure() {
    from("direct:start").log("Querying dbpedia:Ecuador ").to("http://dbpedia.org/page/Ecuador").unmarshal().any23("http://mock.foo/bar").process(
            new Processor() {
      public void process(Exchange exchange) throws Exception {
        ValueFactory vf = SimpleValueFactory.getInstance();
        Model model = (Model) exchange.getIn().getBody();
        IRI propertyLeader = vf.createIRI("http://dbpedia.org/ontology/leader");
        Set<Value> leaders = model.filter(null, propertyLeader, null).objects();

        List listleaders = new ArrayList();
        for (Value leader : leaders) {
          System.out.println(leader);
          listleaders.add(leader.stringValue().replace("resource", "page"));
        }
        exchange.getIn().setBody(listleaders);

      }
    }).log(" Content: ${body} ")
            .split(simple("${body}"))
            .to("direct:extractMoreData");

    from("direct:extractMoreData")
            .log("Split ${body}").convertBodyTo(String.class)
            .toD("${body}")
            .unmarshal().any23("http://mock.foo/bar", Any23Type.JSONLD)
            .log(" Result : ${body} ")
            .to("mock:result");
  }
}
