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
package org.apache.camel.model.dataformat;

import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Any23 data format is used for parsing data to RDF.
 */
@Metadata(firstVersion = "3.0.0", label = "dataformat,transformation", title = "Any23")
@XmlRootElement(name = "any23")
@XmlAccessorType(XmlAccessType.FIELD)
public class Any23DataFormat extends DataFormatDefinition {

  @XmlAttribute
  @Metadata(defaultValue = "RDF4JMODEL")
  private Any23Type outputFormat;
  @XmlElement
  private Map<String, String> configurations;
  @XmlElement
  private List<String> extractors;
  @XmlAttribute
  private String baseURI;

  public Any23DataFormat() {
    super("any23");
  }

  public Any23DataFormat(String baseuri) {
    this();
    this.baseURI = baseuri;
  }

  public Any23DataFormat(String baseuri, Any23Type outputFormat) {
    this(baseuri);
    this.outputFormat = outputFormat;
  }

  public Any23DataFormat(String baseuri, Any23Type outputFormat, Map<String, String> configurations) {
    this(baseuri, outputFormat);
    this.outputFormat = outputFormat;
    this.configurations = configurations;
  }

  public Any23DataFormat(String baseuri, Any23Type outputFormat, Map<String, String> configurations, List<String> extractors) {
    this(baseuri, outputFormat, configurations);
    this.outputFormat = outputFormat;
    this.configurations = configurations;
    this.extractors = extractors;
  }

  public Any23Type getOutputFormat() {
    return outputFormat;
  }

  public void setOutputFormat(Any23Type outputFormat) {
    this.outputFormat = outputFormat;
  }

  public Map<String, String> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(Map<String, String> configurations) {
    this.configurations = configurations;
  }

  public List<String> getExtractors() {
    return extractors;
  }

  public void setExtractors(List<String> extractors) {
    this.extractors = extractors;
  }

  public String getBaseURI() {
    return baseURI;
  }

  public void setBaseURI(String baseURI) {
    this.baseURI = baseURI;
  }

  
}
