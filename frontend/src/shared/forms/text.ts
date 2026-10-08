/** True if the text contains a control character, newlines and tabs included. */
export function hasControlCharacter(value: string): boolean {
  return [...value].some((character) => {
    const code = character.codePointAt(0) ?? 0
    return code < 32 || code === 127
  })
}

/** For multi-line text: newline, carriage return and tab are allowed. */
export function hasDisallowedControlCharacter(value: string): boolean {
  return [...value].some((character) => {
    const code = character.codePointAt(0) ?? 0
    return (code < 32 && code !== 9 && code !== 10 && code !== 13) || code === 127
  })
}
