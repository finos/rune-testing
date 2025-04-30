package com.regnosys.testing.pipeline;

/*-
 * ===============
 * Rosetta Testing
 * ===============
 * Copyright (C) 2022 - 2024 REGnosys
 * ===============
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
 * ===============
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.ImplementedBy;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.rosetta.model.lib.RosettaModelObject;

import javax.xml.validation.Validator;

@ImplementedBy(PipelineFunctionRunnerProviderImpl.class)
public interface PipelineFunctionRunnerProvider {

    PipelineFunctionRunner create(TransformType transformType,
                                  Class<? extends RosettaModelObject> inputType,
                                  Class<?> functionType,
                                  PipelineModel.Serialisation inputSerialisation,
                                  PipelineModel.Serialisation outputSerialisation,
                                  ObjectMapper defaultJsonObjectMapper,
                                  ObjectWriter defaultJsonObjectWriter,
                                  Validator outputXsdValidator);
}
