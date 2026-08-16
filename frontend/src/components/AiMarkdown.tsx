import React from 'react';
import { Box } from '@mui/material';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
// Bundled locally rather than pulled from a CDN, so the fonts resolve offline like the rest of
// the app's assets.
import 'katex/dist/katex.min.css';

/**
 * Renders an assistant reply as Markdown.
 *
 * The assistant answers estimation questions, and a BOQ or a cost breakdown is a table — as plain
 * text those arrive as a wall of pipes. `remark-gfm` is what makes tables, strikethrough and task
 * lists render at all; the base parser does not know them.
 *
 * Maths is rendered rather than forbidden. The model reaches for LaTeX whenever it shows a
 * derivation, and a prompt rule against it produced answers carrying both the LaTeX and a plain
 * text restatement of the same line — so KaTeX turns `$$...$$` into the formula it was meant to be
 * instead of leaving backslashes on screen.
 *
 * Styling is done with descendant selectors rather than a `components` map so that a tag the model
 * uses unexpectedly still comes out looking like the rest of the panel.
 */
const AiMarkdown: React.FC<{ children: string }> = ({ children }) => (
  <Box
    sx={{
      fontSize: '0.875rem',
      lineHeight: 1.6,
      // Collapses the leading and trailing margins so a reply sits flush inside its bubble.
      '& > :first-of-type': { mt: 0 },
      '& > :last-child': { mb: 0 },
      '& p': { my: 0.75 },
      '& h1, & h2, & h3, & h4': {
        // The model emits headings freely; damping the scale keeps a three-heading answer from
        // reading as a document rather than a chat reply.
        fontSize: '0.95rem',
        fontWeight: 700,
        mt: 1.5,
        mb: 0.5,
      },
      '& ul, & ol': { my: 0.75, pl: 2.5 },
      '& li': { mb: 0.25 },
      '& code': {
        px: 0.5,
        borderRadius: 0.5,
        bgcolor: 'action.hover',
        fontSize: '0.8125rem',
      },
      '& pre': {
        p: 1,
        my: 1,
        borderRadius: 1,
        bgcolor: 'action.hover',
        overflowX: 'auto',
        '& code': { bgcolor: 'transparent', px: 0 },
      },
      '& blockquote': {
        my: 1,
        pl: 1.5,
        borderLeft: '3px solid',
        borderColor: 'divider',
        color: 'text.secondary',
      },
      // Tables scroll inside their own box: a BOQ has eight columns and would otherwise force the
      // whole drawer to scroll sideways.
      '& .md-table-scroll': { overflowX: 'auto', my: 1 },
      '& table': { borderCollapse: 'collapse', width: '100%', fontSize: '0.8125rem' },
      '& th, & td': {
        border: '1px solid',
        borderColor: 'divider',
        px: 1,
        py: 0.5,
        textAlign: 'left',
        verticalAlign: 'top',
        whiteSpace: 'nowrap',
      },
      '& th': { bgcolor: 'action.hover', fontWeight: 700 },
      '& a': { color: 'primary.main' },
      // A long derivation is wider than the drawer at its smallest size; scroll the formula
      // rather than the page.
      '& .katex-display': { overflowX: 'auto', overflowY: 'hidden', py: 0.5 },
      '& .katex': { fontSize: '1em' },
    }}
  >
    <ReactMarkdown
      remarkPlugins={[remarkGfm, remarkMath]}
      rehypePlugins={[rehypeKatex]}
      components={{
        table: ({ children: cells }) => (
          <div className="md-table-scroll">
            <table>{cells}</table>
          </div>
        ),
      }}
    >
      {children}
    </ReactMarkdown>
  </Box>
);

export default AiMarkdown;
