/**
 * An indexed entity is returned by the search endpoint and points to a single database record in a model
 */

import type { IndexedEntity } from "metabase-types/api/modelIndexes";
import { createEntity } from "metabase/lib/entities";
import { IndexedEntitySchema } from "metabase/schema";

const IndexedEntities = createEntity({
  name: "indexedEntities",
  nameOne: "indexedEntity",
  // path: "",
  schema: IndexedEntitySchema,
  // api: {},
  objectSelectors: {
    getUrl: (entity: IndexedEntity) => `/model/${entity.model_id}/${entity.id}`, // FIXME: move to URLS lib
    getIcon: () => ({ name: "beaker" }),
  },
});

export default IndexedEntities;
