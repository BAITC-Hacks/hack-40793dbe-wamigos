"use client";

import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import { Icon } from "./icons";

export function Dialog({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const dialog = ref.current;
    dialog?.showModal();
    return () => {
      dialog?.close();
      if (previous?.isConnected) previous.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      aria-labelledby="dialog-title"
    >
      <button
        className="icon-button modal-close"
        onClick={onClose}
        aria-label="Закрыть"
      >
        <Icon name="close" />
      </button>
      <h2 id="dialog-title">{title}</h2>
      {children}
    </dialog>
  );
}
