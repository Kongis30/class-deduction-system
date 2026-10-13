/**
 * 将打印区域复制到应用根节点之外，避免页面布局、滚动容器和导航栏影响浏览器的打印快照。
 * 页面仍负责渲染业务内容；此处只管理打印生命周期和 DOM 隔离。
 */
export function createIsolatedPrinter(getSource: () => HTMLElement | null | undefined) {
  let attached = false;

  // 浏览器直接执行 ⌘P 时也会触发 beforeprint，必须同步准备打印节点。
  function prepare(): boolean {
    const source = getSource();
    if (!source) return false;

    let root = document.getElementById('print-document-root');
    if (!root) {
      root = document.createElement('div');
      root.id = 'print-document-root';
      document.body.appendChild(root);
    }

    const copy = source.cloneNode(true) as HTMLElement;
    // cloneNode 不复制表单控件的实时值；收据编辑后打印也应与屏幕一致。
    const sourceControls = source.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input, textarea, select');
    const copyControls = copy.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input, textarea, select');
    sourceControls.forEach((control, index) => {
      const copied = copyControls[index];
      if (!copied) return;
      if (control instanceof HTMLInputElement && copied instanceof HTMLInputElement) {
        copied.value = control.value;
        copied.checked = control.checked;
      } else if (control instanceof HTMLTextAreaElement && copied instanceof HTMLTextAreaElement) {
        copied.value = control.value;
        copied.textContent = control.value;
      } else if (control instanceof HTMLSelectElement && copied instanceof HTMLSelectElement) {
        copied.value = control.value;
      }
    });

    root.replaceChildren(copy);
    document.body.classList.add('has-isolated-print');
    return true;
  }

  function clear() {
    document.body.classList.remove('has-isolated-print');
    document.getElementById('print-document-root')?.remove();
  }

  function attach() {
    if (attached) return;
    window.addEventListener('beforeprint', prepare);
    window.addEventListener('afterprint', clear);
    attached = true;
  }

  function print(): boolean {
    if (!prepare()) return false;
    window.print();
    return true;
  }

  // 大型表单需要先让浏览器完成样式计算和布局，再打开打印预览。
  async function printWhenReady(): Promise<boolean> {
    if (!prepare()) return false;
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
    });
    if (document.fonts?.status === 'loading') {
      await document.fonts.ready;
    }
    window.print();
    return true;
  }

  function detach() {
    if (attached) {
      window.removeEventListener('beforeprint', prepare);
      window.removeEventListener('afterprint', clear);
      attached = false;
    }
    clear();
  }

  return { attach, print, printWhenReady, detach };
}
