package com.regnosys.testing.pipeline;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2025 REGnosys
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

import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.model.lib.annotations.RosettaAttribute;
import com.rosetta.model.lib.annotations.RosettaDataType;
import com.rosetta.model.lib.annotations.RuneAttribute;
import com.rosetta.model.lib.annotations.RuneDataType;
import com.rosetta.model.lib.meta.RosettaMetaData;
import com.rosetta.model.lib.path.RosettaPath;
import com.rosetta.model.lib.process.BuilderMerger;
import com.rosetta.model.lib.process.BuilderProcessor;
import com.rosetta.model.lib.process.Processor;

import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * @version 0.0.0.master-SNAPSHOT
 */
@RosettaDataType(value="TestObject", builder= TestObject.TestObjectBuilderImpl.class, version="0.0.0.master-SNAPSHOT")
@RuneDataType(value="TestObject", model="cdm", builder= TestObject.TestObjectBuilderImpl.class, version="0.0.0.master-SNAPSHOT")
public interface TestObject extends RosettaModelObject {

	/*********************** Getter Methods  ***********************/
	String getTest();

	/*********************** Build Methods  ***********************/
	TestObject build();
	
	TestObjectBuilder toBuilder();
	
	static TestObjectBuilder builder() {
		return new TestObjectBuilderImpl();
	}

	/*********************** Utility Methods  ***********************/
	@Override
	default RosettaMetaData<? extends TestObject> metaData() {
		return null;
	}
	
	@Override
	@RuneAttribute("@type")
	default Class<? extends TestObject> getType() {
		return TestObject.class;
	}
	
	@Override
	default void process(RosettaPath path, Processor processor) {
		processor.processBasic(path.newSubPath("test"), String.class, getTest(), this);
	}
	

	/*********************** Builder Interface  ***********************/
	interface TestObjectBuilder extends TestObject, RosettaModelObjectBuilder {
		TestObjectBuilder setTest(String test);

		@Override
		default void process(RosettaPath path, BuilderProcessor processor) {
			processor.processBasic(path.newSubPath("test"), String.class, getTest(), this);
		}
		

		TestObjectBuilder prune();
	}

	/*********************** Immutable Implementation of TestObject  ***********************/
	class TestObjectImpl implements TestObject {
		private final String test;
		
		protected TestObjectImpl(TestObjectBuilder builder) {
			this.test = builder.getTest();
		}
		
		@Override
		@RosettaAttribute("test")
		@RuneAttribute("test")
		public String getTest() {
			return test;
		}
		
		@Override
		public TestObject build() {
			return this;
		}
		
		@Override
		public TestObjectBuilder toBuilder() {
			TestObjectBuilder builder = builder();
			setBuilderFields(builder);
			return builder;
		}
		
		protected void setBuilderFields(TestObjectBuilder builder) {
			ofNullable(getTest()).ifPresent(builder::setTest);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || !(o instanceof RosettaModelObject) || !getType().equals(((RosettaModelObject)o).getType())) return false;
		
			TestObject _that = getType().cast(o);
		
			if (!Objects.equals(test, _that.getTest())) return false;
			return true;
		}
		
		@Override
		public int hashCode() {
			int _result = 0;
			_result = 31 * _result + (test != null ? test.hashCode() : 0);
			return _result;
		}
		
		@Override
		public String toString() {
			return "TestObject {" +
				"test=" + this.test +
			'}';
		}
	}

	/*********************** Builder Implementation of TestObject  ***********************/
	class TestObjectBuilderImpl implements TestObjectBuilder {
	
		protected String test;
		
		@Override
		@RosettaAttribute("test")
		@RuneAttribute("test")
		public String getTest() {
			return test;
		}
		
		@Override
		@RosettaAttribute("test")
		@RuneAttribute("test")
		public TestObjectBuilder setTest(String _test) {
			this.test = _test == null ? null : _test;
			return this;
		}
		
		@Override
		public TestObject build() {
			return new TestObjectImpl(this);
		}
		
		@Override
		public TestObjectBuilder toBuilder() {
			return this;
		}
	
		@SuppressWarnings("unchecked")
		@Override
		public TestObjectBuilder prune() {
			return this;
		}
		
		@Override
		public boolean hasData() {
			if (getTest()!=null) return true;
			return false;
		}
	
		@SuppressWarnings("unchecked")
		@Override
		public TestObjectBuilder merge(RosettaModelObjectBuilder other, BuilderMerger merger) {
			TestObjectBuilder o = (TestObjectBuilder) other;
			
			
			merger.mergeBasic(getTest(), o.getTest(), this::setTest);
			return this;
		}
	
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || !(o instanceof RosettaModelObject) || !getType().equals(((RosettaModelObject)o).getType())) return false;
		
			TestObject _that = getType().cast(o);
		
			if (!Objects.equals(test, _that.getTest())) return false;
			return true;
		}
		
		@Override
		public int hashCode() {
			int _result = 0;
			_result = 31 * _result + (test != null ? test.hashCode() : 0);
			return _result;
		}
		
		@Override
		public String toString() {
			return "TestObjectBuilder {" +
				"test=" + this.test +
			'}';
		}
	}
}
