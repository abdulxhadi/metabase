import React, {
  ChangeEvent,
  KeyboardEvent,
  forwardRef,
  HTMLAttributes,
  Ref,
  useCallback,
  useEffect,
  useState,
  useRef,
} from "react";

import { usePrevious } from "react-use";

import {
  EditableTextArea,
  EditableTextRoot,
  Markdown,
} from "./EditableText.styled";

export type EditableTextAttributes = Omit<
  HTMLAttributes<HTMLDivElement>,
  "onChange"
>;

export interface EditableTextProps extends EditableTextAttributes {
  initialValue?: string | null;
  placeholder?: string;
  isEditing?: boolean;
  isOptional?: boolean;
  isMultiline?: boolean;
  isDisabled?: boolean;
  isMarkdownSupported?: boolean;
  onChange?: (value: string) => void;
  onFocus?: () => void;
  onBlur?: () => void;
  "data-testid"?: string;
}

const EditableText = forwardRef(function EditableText(
  {
    initialValue,
    placeholder,
    isEditing = false,
    isOptional = false,
    isMultiline = false,
    isDisabled = false,
    isMarkdownSupported = false,
    onChange,
    onFocus,
    onBlur,
    "data-testid": dataTestId,
    ...props
  }: EditableTextProps,
  ref: Ref<HTMLDivElement>,
) {
  const [inputValue, setInputValue] = useState(initialValue ?? "");
  const [submitValue, setSubmitValue] = useState(initialValue ?? "");
  const [escapedInputValue, setEscapedInputValue] = useState(
    initialValue ?? "",
  );
  const [isInFocus, setIsInFocus] = useState(isEditing);
  const input = useRef<HTMLTextAreaElement>(null);
  const displayValue = inputValue ? escapedInputValue : placeholder;
  const submitOnBlur = useRef(true);
  const previousInitialValue = usePrevious(initialValue);

  useEffect(() => {
    if (initialValue && initialValue !== previousInitialValue) {
      setInputValue(initialValue);
    }
  }, [initialValue, previousInitialValue]);

  useEffect(() => {
    if (isInFocus) {
      input.current?.focus();
    }
  }, [isInFocus]);

  const handleBlur = useCallback(
    e => {
      setIsInFocus(false);
      if (!isOptional && !inputValue) {
        setInputValue(submitValue);
      } else if (inputValue !== submitValue && submitOnBlur.current) {
        setSubmitValue(inputValue);
        onChange?.(inputValue);
      }
      onBlur?.();
    },
    [inputValue, submitValue, isOptional, onChange, onBlur],
  );

  const handleChange = useCallback(
    (event: ChangeEvent<HTMLTextAreaElement>) => {
      setInputValue(event.currentTarget.value);
      setEscapedInputValue(event.currentTarget.value);
      submitOnBlur.current = true;
    },
    [submitOnBlur],
  );

  const handleFocus = () => {
    setIsInFocus(true);
    onFocus?.();
  };

  const handleKeyDown = useCallback(
    (event: KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key === "Escape") {
        setInputValue(submitValue);
        submitOnBlur.current = false;
        event.currentTarget.blur();
      } else if (event.key === "Enter" && !isMultiline) {
        event.preventDefault();
        submitOnBlur.current = true;
        event.currentTarget.blur();
      }
    },
    [submitValue, isMultiline],
  );

  return (
    <EditableTextRoot
      {...props}
      ref={ref}
      isEditing={isEditing}
      isDisabled={isDisabled}
      data-value={`${displayValue}\u00A0`}
      onClick={() => {
        setIsInFocus(true);
      }}
    >
      {isInFocus || !isMarkdownSupported ? (
        <EditableTextArea
          ref={input}
          value={inputValue}
          placeholder={placeholder}
          disabled={isDisabled}
          data-testid={dataTestId}
          onFocus={handleFocus}
          onBlur={handleBlur}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
        />
      ) : (
        <Markdown disallowHeading>{escapedInputValue}</Markdown>
      )}
    </EditableTextRoot>
  );
});

export default Object.assign(EditableText, { Root: EditableTextRoot });
