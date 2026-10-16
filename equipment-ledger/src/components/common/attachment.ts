export type AttachmentItem = {
  id: string;
  name?: string;
  url?: string;
  previewUrl?: string;
  dataUrl?: string;
  contentType?: string;
  size?: number;
  isImage?: boolean;
};
export function isAttachmentImage(file: AttachmentItem) {
  return (
    file.isImage ??
    (file.contentType?.startsWith('image/') ||
      /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(file.name || ''))
  );
}
export function attachmentUrl(file: AttachmentItem) {
  return file.previewUrl || file.url || file.dataUrl || '';
}
