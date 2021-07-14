package processing.mode.java;

import org.eclipse.jdt.core.dom.*;
import processing.app.*;
import processing.app.syntax.SyntaxDocument;
import processing.app.ui.*;
import processing.mode.java.JavaEditor;

import javax.swing.text.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.function.Consumer;

public class Searcher {
  private final JavaEditor editor;
  private final PreprocService pps;

  Searcher(JavaEditor editor, PreprocService pps) {

    this.editor = editor;
    this.pps = pps;
  }
  public void search(Search s, Consumer<ArrayList<SearchResult>> callback) {
    pps.whenDoneBlocking(ps -> handleSearch(ps, s, callback));
  }

  private void handleSearch(PreprocSketch ps, Search search, Consumer<ArrayList<SearchResult>> callback) {
    String text = search.getText();
    ArrayList<SearchResult> occurrences = new ArrayList<>();
    boolean globalGoto = text.charAt(0) == '@';
    String num = globalGoto? text.substring(1) : text;
    if (num.length()>0 && num.length()<10 && num.chars().allMatch(c -> c>='0' && c<='9') && num.charAt(0)!='0') { // goto
      occurrences.add(new GotoSearchResult(search, Integer.parseInt(num), ps, globalGoto));
    } else {
      ps.compilationUnit.getRoot().accept(new ASTVisitor() {
        @Override
        public boolean visit(TypeDeclaration name) {
          add(name);
          return super.visit(name);
        }
        @Override
        public boolean visit(EnumDeclaration name) {
          add(name);
          return super.visit(name);
        }

        public void add(AbstractTypeDeclaration name) {
          if (search.match(name.getName().getIdentifier())) occurrences.add(new SNSearchResult(search, name, ps));
        }
      });
    }
    callback.accept(occurrences);
  }

  class GotoSearchResult extends SearchResult {

    private final int ln;
    private final PreprocSketch ps;
    private final boolean global;
    public GotoSearchResult(Search s, int ln, PreprocSketch ps, boolean global) {
      super(s);
      this.ln = ln;
      this.ps = ps;
      this.global = global;
    }
    public void select() {
      EventQueue.invokeLater(() -> {
        JavaTextArea ta = editor.getJavaTextArea();
        int pdeOff, pdeTab;
        if (global) {

          // mostly copied from JavaBuild.placeException
          Sketch sketch = ps.sketch;
          int javaLine = ln-1;
          int tabPos = 0;
          int tabLine = 0;
          for (int i = 0; i < sketch.getCodeCount(); i++) {
            SketchCode code = sketch.getCode(i);
            if (code.isExtension("pde")) {
              if (code.getPreprocOffset() <= javaLine) {
                tabPos = i;
                tabLine = javaLine - code.getPreprocOffset();
              }
            }
          }
          pdeTab = tabPos;
          editor.getSketch().setCurrentCode(pdeTab); // so getLineStartOffset works correctly
          pdeOff = ta.getLineStartOffset(tabLine);

        } else {
          pdeOff = ta.getLineStartOffset(ln-1);
          pdeTab = editor.getSketch().getCurrentCodeIndex();
        }
        editor.highlight(pdeTab, pdeOff, pdeOff);
      });
    }
    @Override public String toString() {
      return global? "go to Java line "+ln : "go to line "+ln;
    }
  }

  class SNSearchResult extends SearchResult {

    private final AbstractTypeDeclaration item;
    private final PreprocSketch ps;

    SNSearchResult(Search s, AbstractTypeDeclaration item, PreprocSketch ps) {
      super(s);
      this.item = item;
      this.ps = ps;
    }

    public void select() {
      SketchInterval si = ps.mapJavaToSketch(item.getName());
      if (!ps.inRange(si)) return;
      EventQueue.invokeLater(() -> editor.highlight(si.tabIndex, si.startTabOffset, si.stopTabOffset));
      close();
    }

    @Override public String toString() {
      return item.getName().getIdentifier();
    }
  }
}
