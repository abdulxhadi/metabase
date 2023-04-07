import React, { ComponentPropsWithRef } from "react";
import remarkGfm from "remark-gfm";
import { MarkdownRoot } from "./Markdown.styled";

const REMARK_PLUGINS = [remarkGfm];

export interface MarkdownProps
  extends ComponentPropsWithRef<typeof MarkdownRoot> {
  className?: string;
  disallowHeading?: boolean;
  disableLink?: boolean;
  children: string;
}

const Markdown = ({
  className,
  children = "",
  disallowHeading = false,
  disableLink = false,
  ...rest
}: MarkdownProps): JSX.Element => {
  const additionalOptions = disallowHeading
    ? {
        disallowedElements: ["h1", "h2", "h3", "h4", "h5", "h6"],
        unwrapDisallowed: true,
      }
    : {};

  return (
    <MarkdownRoot
      className={className}
      remarkPlugins={REMARK_PLUGINS}
      {...additionalOptions}
      {...rest}
    >
      {children}
    </MarkdownRoot>
  );
};

export default Markdown;
