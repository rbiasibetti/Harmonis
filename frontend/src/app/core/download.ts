/** Dispara o download de um Blob no navegador com o nome informado. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename || 'arquivo';
  a.click();
  URL.revokeObjectURL(url);
}
