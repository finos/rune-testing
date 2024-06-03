package com.regnosys.testing;

/*-
 * ===============
 * Rune Testing
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


import com.regnosys.rosetta.common.reports.ReportField;
import com.regnosys.rosetta.common.serialisation.RosettaDataValueObjectToString;
import com.rosetta.model.lib.reports.Tabulator;

import java.util.ArrayList;
import java.util.List;

public class FieldValueFlattener implements Tabulator.FieldValueVisitor<List<FieldValueFlattener.ParentAndIndex>> {
    public List<ReportField> accumulator = new ArrayList<>();

    @Override
    public void visitSingle(Tabulator.FieldValue fieldValue, List<ParentAndIndex> parentsAndIndices) {
        if (fieldValue.getValue().isPresent()) {
            String value = RosettaDataValueObjectToString.toValueString(fieldValue.getValue().get());
            accumulator.add(new ReportField(
                    computeFieldName(fieldValue, parentsAndIndices),
                    ((Tabulator.FieldImpl)fieldValue.getField()).getRuleId().map(Object::toString).orElse(null),
                    parentsAndIndices.isEmpty() ? null : parentsAndIndices.get(parentsAndIndices.size()-1).index,
                    value,
                    ""
            ));
        }
    }
    private static String computeFieldName(Tabulator.FieldValue fieldValue, List<ParentAndIndex> parentsAndIndices) {
        if (parentsAndIndices.isEmpty()) {
            return fieldValue.getField().getName();
        }
        StringBuilder result = new StringBuilder();
        result
                .append(parentsAndIndices.get(0).parent)
                .append(" -> ");
        for (int i=1; i<parentsAndIndices.size(); i++) {
            result
                    .append(insertIndex(parentsAndIndices.get(i).parent, parentsAndIndices.get(i-1).index))
                    .append(" -> ");
        }
        result.append(insertIndex(fieldValue.getField().getName(), parentsAndIndices.get(parentsAndIndices.size()-1).index));
        return result.toString();
    }
    private static String insertIndex(String fieldName, Integer index) {
        if (index == null) {
            return fieldName;
        }
        if (fieldName.contains("$")) {
            return fieldName.replace("$", index.toString());
        }
        return fieldName + " (" + index + ")";
    }
    @Override
    public void visitNested(Tabulator.NestedFieldValue nestedFieldValue, List<ParentAndIndex> parentsAndIndices) {
        List<ParentAndIndex> newParentsAndIndices = new ArrayList<>(parentsAndIndices);
        newParentsAndIndices.add(new ParentAndIndex(nestedFieldValue.getField().getName(), null));
        nestedFieldValue.getValue().ifPresent(
                (v) -> v.forEach(
                        sub -> sub.accept(this, newParentsAndIndices)
                )
        );
    }
    @Override
    public void visitMultiNested(Tabulator.MultiNestedFieldValue multiNestedFieldValue, List<ParentAndIndex> parentsAndIndices) {
        multiNestedFieldValue.getValue().ifPresent(
                (vs) -> {
                    for (int i=0; i<vs.size(); i++) {
                        int repeatableIndex = i + 1;
                        List<ParentAndIndex> newParentsAndIndices = new ArrayList<>(parentsAndIndices);
                        newParentsAndIndices.add(new ParentAndIndex(multiNestedFieldValue.getField().getName(), repeatableIndex));
                        vs.get(i).forEach(
                                sub -> sub.accept(this, newParentsAndIndices)
                        );
                    }
                }
        );
    }

    public static class ParentAndIndex {
        public final String parent;
        public final Integer index;

        public ParentAndIndex(String parent, Integer index) {
            this.parent = parent;
            this.index = index;
        }
    }
}
