declare module 'pannellum' {}
interface PannellumViewer {
  destroy(): void;
  lookAt(pitch: number, yaw: number, hfov: number, animated?: boolean): PannellumViewer;
  on(event: 'load' | 'error', callback: () => void): PannellumViewer;
}
interface Window {
  pannellum: { viewer(container: HTMLElement, options: {
    type: 'equirectangular'; panorama: string; autoLoad: boolean; autoRotate: number;
    orientationOnByDefault: boolean; showControls: boolean; showFullscreenCtrl: boolean;
    compass: boolean; hfov: number; pitch: number; yaw: number; escapeHTML: boolean;
    strings: Record<string, string>;
  }): PannellumViewer };
}
