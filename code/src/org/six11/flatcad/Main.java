package org.six11.flatcad;

import org.six11.flatcad.command.*;
import org.six11.flatcad.geom.Box;
import org.six11.flatcad.geom.Pyramid;
import org.six11.flatcad.geom.HalfEdge;
import org.six11.flatcad.model.TurtleModel;
import org.six11.flatcad.hpgl.HPGL;
import org.six11.flatcad.flatlang.FlatInterpreter;
import org.six11.flatcad.flatlang.DebugUI;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.Debug;
import org.six11.util.io.FileUtil;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import org.lwjgl.LWJGLException;
import org.six11.flatcad.gl.OpenGLDisplay;
import javax.swing.JTextArea;
import javax.swing.JSplitPane;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 
 **/
public class Main implements ActionListener {

	TextEntry textEntry;
	JPanel buttonPanel;
	OpenGLDisplay display;
	FlatInterpreter interpreter;
	ApplicationFrame af;
	int loggingSeconds = 0;
	DebugUI debugUI;

	public static void main(String[] args) {
		Main m = new Main();
		m.run();
		List<File> dirs = new ArrayList<File>();
		boolean loadedFile = false;
		String loadMe = null;
		boolean loadFileInAnonymousBuffer = false;
		String logFilePrefix = null;

		for (String s : args) {
			if (s.startsWith("--flatpath=")) {
				String bigPath = s.substring("--flatpath=".length());
				StringTokenizer tok = new StringTokenizer(bigPath, ":");
				while (tok.hasMoreTokens()) {
					dirs.add(new File(tok.nextToken()));
				}
			} else if (s.equals("--noedit")) {
				loadFileInAnonymousBuffer = true;
			} else if (s.startsWith("--logfile=")) {
				logFilePrefix = s.substring("--logfile=".length());
			} else if (s.equals("--no-color")) {
				Debug.useColor = false;
			} else if (!loadedFile) {
				loadMe = s;
			} else {
				Debug.out("Main",
						"So which file do you want me to run, anyway?");
			}
		}
		if (dirs.size() == 0) {
			dirs.add(new File("."));
		}
		m.getInterpreter().setPaths(dirs);
		if (!m.getInterpreter().loadFile(true, "builtin.fl", "fl/builtin.fl")) {
			Debug.out("Main",
					"ERROR: Can't find the builtin.fl file. This is basically");
			Debug.out("Main",
					"something you need to have, so I'll just quit now. Sorry.");
			System.exit(0);
		}

		if (loadMe != null) {
			m.textEntry.loadFile(loadMe, loadFileInAnonymousBuffer);
		}

		if (logFilePrefix != null) {
			m.setLogging(logFilePrefix, 20);
		}
	}

	public String getLoggingPreamble() {
		return "; Autosave after " + loggingSeconds + "\n\n";
	}

	public void setLogging(final String prefix, final int seconds) {
		Timer timer = new Timer();
		TimerTask log = new TimerTask() {
			public void run() {
				try {
					loggingSeconds = loggingSeconds + seconds;
					String contents = getLoggingPreamble()
							+ textEntry.getText();
					String fileName = prefix + "-" + System.currentTimeMillis()
							+ ".fl";
					FileUtil.writeStringToFile(fileName, contents);
				} catch (IOException ex) {
					Debug.out("Main", "Got exception. Can I write to disk?");
					ex.printStackTrace();
				}
			}
		};
		loggingSeconds = 0;
		timer.schedule(log, (long) 1000 * seconds, (long) 1000 * seconds);
	}

	public void run() {
		af = new ApplicationFrame("FlatCAD", 800, 600);
		interpreter = new FlatInterpreter();
		initTextEntry();
		try {
			// 1. Set up the OpenGL display component.
			display = new OpenGLDisplay();
			// display.setPreferredSize(new Dimension(250, 250));
			display.setModel(interpreter.getSoup()/* turtleModel */);

			// 2. Set up the buttons and text input panel.
			JPanel buttonsAndText = new JPanel();
			buttonsAndText.setLayout(new BorderLayout());
			buttonPanel = initButtons();
			buttonsAndText.add(buttonPanel, BorderLayout.NORTH);
			buttonsAndText.add(textEntry, BorderLayout.CENTER);

			// 3. Set up the split pane with buttons/Text on left, and opengl
			// display on right
			JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
					buttonsAndText, display);
			// splitPane.setResizeWeight(0.5);

			// 4. Add the split pane to the top-level frame.
			af.add(splitPane);
		} catch (LWJGLException ex) {
			Debug.out("Main", "Caught GL exception, so I will die now.");
			ex.printStackTrace();
			System.exit(-1);
		}
		initCommands();
		af.setVisible(true);

		TimerTask tt = new TimerTask() {
			public void run() {
				try {
					SwingUtilities.invokeAndWait(new Runnable() { // Wait,
																	// AWT/Swing
																	// is
																	// running
																	// on the
																	// same
																	// Thread
																	// now
								public void run() {
									display.update(display.getGraphics());
								}
							});
				} catch (InterruptedException ex) {
					Debug.out("Main",
							"Caught an interrupted exception while trying to cause GL update.");
				} catch (InvocationTargetException ex) {
					Debug.out("Main",
							"Caught an invocation target exception while trying to cause GL update.");
				}
			}
		};
		// by using the Timer, it ensures that AWT/Swing is able to do the
		// required updates, between each TimerTask execution.
		Timer t = new Timer();
		t.scheduleAtFixedRate(tt, 0, 30);

	}

	public FlatInterpreter getInterpreter() {
		return interpreter;
	}

	private void initTextEntry() {
		textEntry = new TextEntry();
		textEntry.addFileNameListener(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent ev) {
				String newName = (String) ev.getNewValue();
				af.setTitle("FlatCAD: " + newName);
			}
		});
		// textEntry.setColorScheme(TextEntry.COLOR_SCHEME_HACKER);
		textEntry.setColorScheme(TextEntry.COLOR_SCHEME_DEMO);
		// HPGL.initInstance(textEntry);
	}

	private JPanel initButtons() {
		JPanel ret = new JPanel();
		ret.add(makeButton("Run"));
		ret.add(makeButton("Run Selection"));
		ret.add(makeButton("Open..."));
		ret.add(makeButton("Save"));
		ret.add(makeButton("Save As..."));
		ret.add(makeButton("Debug"));
		return ret;
	}

	private void initCommands() {
		textEntry.addUserInputListener(interpreter);
		interpreter.setComponent(display);
	}

	private ChangeListener makeChangeListener() {
		return new ChangeListener() {
			public void stateChanged(ChangeEvent ev) {
				Debug.out("Main", "Whacking display to repaint.");
				display.repaint();
			}
		};
	}

	private JButton makeButton(String name) {
		JButton ret = new JButton(name);
		ret.addActionListener(this);
		return ret;
	}

	public void actionPerformed(ActionEvent ev) {
		String s = ev.getActionCommand();
		if (s.equals("Run")) {
			textEntry.interpret();
		}
		if (s.equals("Run Selection")) {
			textEntry.interpretSelection();
		}
		if (s.equals("Debug")) {
			debug();
		}
		if (s.equals("Open...")) {
			textEntry.open();
		}
		if (s.equals("Save")) {
			textEntry.save();
		}
		if (s.equals("Save As...")) {
			textEntry.saveAs();
		}
	}

	public void debug() {
		if (debugUI == null) {
			debugUI = new DebugUI(interpreter, textEntry);
			Debug.out("Main",
					"just made a debug ui. class is: " + debugUI.getClass());
		}
		debugUI.go();
	}

}
