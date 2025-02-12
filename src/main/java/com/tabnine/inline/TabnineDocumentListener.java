package com.tabnine.inline;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.tabnine.capabilities.SuggestionsMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TabnineDocumentListener implements DocumentListener {

  private final InlineCompletionHandler handler = new InlineCompletionHandler(true);

  private static final AtomicBoolean isMuted = new AtomicBoolean(false);

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    if (isMuted.get()
        || SuggestionsMode.getSuggestionMode() != SuggestionsMode.INLINE
        || event.getNewFragment().toString().equals(CompletionUtil.DUMMY_IDENTIFIER)
        || event.getNewLength() < 1) {
      return;
    }
    Document document = event.getDocument();
    if (ObjectUtils.doIfCast(document, DocumentEx.class, DocumentEx::isInBulkUpdate)
        == Boolean.TRUE) {
      return;
    }
    Editor editor = getActiveEditor(document);

    if( editor != null && !editor.getEditorKind() .equals(EditorKind.MAIN_EDITOR) && !ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    Project project = ObjectUtils.doIfNotNull(editor, Editor::getProject);
    PsiFile file =
        ObjectUtils.doIfNotNull(
            project, proj -> PsiDocumentManager.getInstance(proj).getPsiFile(document));
    if (editor != null) {
      CompletionPreview.disposeIfExists(editor, preview -> event.getNewLength() > 1);
    }
    if (editor == null || project == null || file == null) {
      return;
    }
    handler.invoke(project, editor, file, event.getOffset() + event.getNewLength());
  }

  public static void mute() {
    isMuted.set(true);
  }

  public static void unmute() {
    isMuted.set(false);
  }

  @Nullable
  private static Editor getActiveEditor(@NotNull Document document) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      return null;
    }
    Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    DataContext dataContext = DataManager.getInstance().getDataContext(focusOwner);
    // ignore caret placing when exiting
    Editor activeEditor =
        ApplicationManager.getApplication().isDisposed()
            ? null
            : CommonDataKeys.EDITOR.getData(dataContext);
    if (activeEditor != null && activeEditor.getDocument() != document) {
      activeEditor = null;
    }
    return activeEditor;
  }
}
