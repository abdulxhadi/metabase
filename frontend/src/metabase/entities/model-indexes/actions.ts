import _ from "underscore";

import { Dispatch } from "metabase-types/store";
import type { ModelIndex } from "metabase-types/api/modelIndexes";
import type Question from "metabase-lib/Question";
import type Field from "metabase-lib/metadata/Field";

import ModelIndexes from "./model-indexes";

type FieldWithMaybeIndex = Field & { should_index?: boolean };

export const updateModelIndexes =
  async (model: Question) => async (dispatch: Dispatch, getState: any) => {
    const fields = model.getResultMetadata();

    const fieldsWithIndexes = fields.filter(
      (field: FieldWithMaybeIndex) => field.should_index !== undefined,
    );

    console.log("fieldsWithIndexes", fieldsWithIndexes);

    if (fieldsWithIndexes.length === 0) {
      return;
    }

    const [fieldsToIndex, fieldsToUnindex] = _.partition(
      fieldsWithIndexes,
      (field: FieldWithMaybeIndex) => field.should_index,
    );

    const existingIndexes: ModelIndex[] =
      ModelIndexes.selectors.getIndexesForModel(getState(), {
        modelId: model.id(),
      });

    // add  new indexes
    const dedupedFieldsToIndex = fieldsToIndex.filter(
      field =>
        !existingIndexes.some((index: ModelIndex) =>
          _.isEqual(index.value_ref, field.field_ref),
        ),
    );

    if (dedupedFieldsToIndex.length) {
      const pkRef = ModelIndexes.utils.getPkRef(fields);

      await dedupedFieldsToIndex.map(field =>
        ModelIndexes.api.create({
          model_id: model.id(),
          value_ref: field.field_ref,
          pk_ref: pkRef,
        }),
      );
    }

    // remove deleted indexes
    const indexIdsToRemove = fieldsToUnindex.reduce(
      (indexIdsToRemove, field) => {
        const foundIndex = existingIndexes.find((index: ModelIndex) =>
          _.isEqual(index.value_ref, field.field_ref),
        );
        if (foundIndex) {
          indexIdsToRemove.push(foundIndex.id);
        }
        return indexIdsToRemove;
      },
      [],
    );

    if (indexIdsToRemove.length) {
      await indexIdsToRemove.map((indexId: number) =>
        ModelIndexes.api.delete({ id: indexId }),
      );
    }

    dispatch(ModelIndexes.actions.invalidateLists());
  };

export function cleanIndexFlags(model: Question) {
  const fields = model.getResultMetadata();
  const indexesToClean = fields.reduce(
    (
      indexesToClean: number[],
      field: FieldWithMaybeIndex,
      thisIndex: number,
    ) => {
      if (field.should_index !== undefined) {
        indexesToClean.push(thisIndex);
      }
      return indexesToClean;
    },
    [],
  );

  for (const index of indexesToClean) {
    delete model.getResultMetadata()[index].should_index;
  }
  return model;
}
