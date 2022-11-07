import { createContext, useContext } from "react";

import _ from "lodash";

import type { Column, Value } from "metabase-types/types/Dataset";
import { DataApp } from "metabase-types/api";

type FieldName = string;
type CardName = string;

export type ObjectDetailField = {
  column: Column;
  value: Value;
};

export type FormattedObjectDetail = Record<FieldName, ObjectDetailField>;

export type DataContextType = Record<CardName, FormattedObjectDetail>;

export type DataAppContextType = {
  isDataApp: boolean;
  dataApp: DataApp | null;
  data: DataContextType;
  bulkActions: {
    cardId: number | null;
    selectedRowIndexes: number[];
    addRow: (cardId: number, index: number) => void;
    removeRow: (index: number) => void;
    clearSelection: () => void;
  };
  isLoaded: boolean;
  format: (text: string) => string;
};

export const DataAppContext = createContext<DataAppContextType>({
  isDataApp: true,
  data: {},
  dataApp: null,
  bulkActions: {
    cardId: null,
    selectedRowIndexes: [],
    addRow: _.noop,
    removeRow: _.noop,
    clearSelection: _.noop,
  },
  isLoaded: true,
  format: (text: string) => text,
});

export function useDataAppContext() {
  return useContext(DataAppContext);
}
