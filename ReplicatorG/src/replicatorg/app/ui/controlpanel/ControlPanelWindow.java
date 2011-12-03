/*
 Part of the ReplicatorG project - http://www.replicat.org
 Copyright (c) 2008 Zach Smith

 Forked from Arduino: http://www.arduino.cc

 Based on Processing http://www.processing.org
 Copyright (c) 2004-05 Ben Fry and Casey Reas
 Copyright (c) 2001-04 Massachusetts Institute of Technology

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 
 $Id: MainWindow.java 370 2008-01-19 16:37:19Z mellis $
 */

package replicatorg.app.ui.controlpanel;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;
import replicatorg.app.Base;
import replicatorg.app.ui.controlpanel.ExtruderPanel;
import replicatorg.app.ui.controlpanel.JogPanel;
import replicatorg.drivers.commands.HomeAxes;
import replicatorg.drivers.commands.InvalidatePosition;
import replicatorg.drivers.commands.DriverCommand.LinearDirection;
import replicatorg.machine.MachineInterface;
import replicatorg.machine.MachineListener;
import replicatorg.machine.MachineProgressEvent;
import replicatorg.machine.MachineState;
import replicatorg.machine.MachineStateChangeEvent;
import replicatorg.machine.MachineToolStatusEvent;
import replicatorg.machine.model.AxisId;
import replicatorg.machine.model.Endstops;
import replicatorg.machine.model.ToolModel;

public class ControlPanelWindow extends JFrame implements
		ChangeListener, WindowListener,
		MachineListener {
	// Autogenerated by serialver
	static final long serialVersionUID = -3494348039028986935L;

	protected JPanel mainPanel;

	protected JogPanel jogPanel;

	protected JTabbedPane toolsPane;

	protected MachineInterface machine;

//	protected Driver driver;

	protected UpdateThread updateThread;

	protected PollThread pollThread;

	private static ControlPanelWindow instance = null;

	public static synchronized ControlPanelWindow getControlPanel(MachineInterface machine2) {
		if (instance == null) {
			instance = new ControlPanelWindow(machine2);
		} else {
			if (instance.machine != machine2) {
				instance.dispose();
				instance = new ControlPanelWindow(machine2);
			}
		}
		return instance;
	}
	
	private ControlPanelWindow(MachineInterface machine) {
		super("Control Panel");

		Image icon = Base.getImage("images/icon.gif", this);
		setIconImage(icon);
		
		// save our machine!
		this.machine = machine;
//		driver = machine.getDriver();
		
		machine.runCommand(new InvalidatePosition());

		// Listen to it-- stop and close if we're in build mode.
		Base.getMachineLoader().addMachineListener(this);
		
		// default behavior
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		// no menu bar.
		setJMenuBar(createMenuBar());

		// create all our GUI interfaces
		mainPanel = new JPanel();
		mainPanel.setLayout(new MigLayout("gap 5, ins 5"));
		mainPanel.add(createJogPanel(),"split 2,flowy, growx");
		mainPanel.add(createActivationPanel(),"flowy, growx");
		mainPanel.add(createToolsPanel(),"spany,grow");
		
		this.setResizable(false);
		add(mainPanel);

		// add our listener hooks.
		addWindowListener(this);
		// addWindowFocusListener(this);
		// addWindowStateListener(this);

		// start our various threads.
		updateThread = new UpdateThread(this);
		updateThread.start();
		pollThread = new PollThread(machine);
		pollThread.start();
	}

	private JMenuItem makeHomeItem(String name,final EnumSet<AxisId> axes,final boolean positive) {
		JMenuItem item = new JMenuItem(name);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				LinearDirection direction;
				if (positive) {
					direction = LinearDirection.POSITIVE;
				} else {
					direction = LinearDirection.NEGATIVE;
				}
				machine.runCommand(new HomeAxes(axes, direction));
			}
		});
		return item;
	}

	protected JMenuBar createMenuBar() {
		JMenuBar bar = new JMenuBar();
		JMenu homeMenu = new JMenu("Homing");
		bar.add(homeMenu);
		
		//adding the appropriate homing options for your endstop configuration
		for (AxisId axis : AxisId.values())
		{
			Endstops endstops = machine.getDriver().getMachine().getEndstops(axis);
			if (endstops != null)
			{
				if (endstops.hasMin == true)
					homeMenu.add(makeHomeItem("Home "+axis.name()+" to minimum",EnumSet.of(axis),false));
				if (endstops.hasMax == true)
					homeMenu.add(makeHomeItem("Home "+axis.name()+" to maximum",EnumSet.of(axis),true));
			}
		}
		
		// These homing options can be dangerous on some machines, especially ones that require sequential
		// homes.  We'll leave them out until we can improve the safety of these operations.
		/*
		homeMenu.add(new JSeparator());
		homeMenu.add(makeHomeItem("Home XY+",EnumSet.of(Axis.X,Axis.Y),true));
		homeMenu.add(makeHomeItem("Home XY-",EnumSet.of(Axis.X,Axis.Y),false));
		homeMenu.add(makeHomeItem("Home all+",EnumSet.allOf(Axis.class),true));
		homeMenu.add(makeHomeItem("Home all-",EnumSet.allOf(Axis.class),false));
		*/
		return bar;
	}

	protected JTextField createDisplayField() {
		int textBoxWidth = 160;

		JTextField tf = new JTextField();
		tf.setMaximumSize(new Dimension(textBoxWidth, 25));
		tf.setMinimumSize(new Dimension(textBoxWidth, 25));
		tf.setPreferredSize(new Dimension(textBoxWidth, 25));
		tf.setEnabled(false);
		return tf;
	}

	protected JComponent createJogPanel() {
		jogPanel = new JogPanel(machine);
		return jogPanel;
	}

	/**
	 * The activation panel contains functions related to pausing, starting, and
	 * powering the steppers up or down.
	 */
	protected JComponent createActivationPanel() {
		JPanel activationPanel = new JPanel();
		
		//R2C2: no need to Stepper Motors Control panel
		//activationPanel.setBorder(BorderFactory
				//.createTitledBorder("Stepper Motor Controls"));
		//activationPanel.setLayout(new BoxLayout(activationPanel,
				//BoxLayout.LINE_AXIS));

		// / Enable/disable steppers.
		JButton enableButton = new JButton("Enable");
		enableButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				machine.runCommand(new replicatorg.drivers.commands.EnableDrives());
			}
		});
		//activationPanel.add(enableButton);

		JButton disableButton = new JButton("Disable");
		disableButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				machine.runCommand(new replicatorg.drivers.commands.DisableDrives());
			}
		});
		//activationPanel.add(disableButton);

		activationPanel.add(Box.createHorizontalGlue());

		return activationPanel;
	}

	Vector<ExtruderPanel> extruderPanels = new Vector<ExtruderPanel>();
	
	protected JComponent createToolsPanel() {
		toolsPane = new JTabbedPane();

		for (Enumeration<ToolModel> e = machine.getModel().getTools().elements(); e.hasMoreElements();) {
			ToolModel t = e.nextElement();
			if (t == null) continue;
			if (t.getType().equals("extruder")) {
				Base.logger.fine("Creating panel for " + t.getName());
				ExtruderPanel extruderPanel = new ExtruderPanel(machine,t);
				toolsPane.addTab(t.getName(),extruderPanel);
				extruderPanels.add(extruderPanel);
				if (machine.getModel().currentTool() == t) {
					toolsPane.setSelectedComponent(extruderPanel);
				}
			} else {
				Base.logger.warning("Unsupported tool for control panel.");
			}
		} 
		toolsPane.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent ce) {
				final JTabbedPane tp = (JTabbedPane)ce.getSource();
				final ExtruderPanel ep = (ExtruderPanel)tp.getSelectedComponent();
//				machine.getModel().selectTool(ep.getTool().getIndex());
				machine.runCommand(new replicatorg.drivers.commands.SelectTool(ep.getTool().getIndex()));
			}
		});
		return toolsPane;
	}
	
	public void updateStatus() {
		jogPanel.updateStatus();
		for (ExtruderPanel e : extruderPanels) {
			e.updateStatus();
		}
	}
	
	public void windowClosing(WindowEvent e) {
		updateThread.interrupt();
		pollThread.interrupt();
	}

	public void windowClosed(WindowEvent e) {
		synchronized(getClass()) {
			// TODO: add this back in.
//			machine.removeMachineStateListener(this);
			if (instance == this) {
				instance = null;
			}
		}
	}

	public void windowOpened(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	class PollThread extends Thread {
		MachineInterface machine;

		public PollThread(MachineInterface machine) {
			super("Control Panel Poll Thread");

			this.machine = machine;
		}

		public void run() {
			// we'll break on interrupts
			try {
				while (true) {
					machine.runCommand(new replicatorg.drivers.commands.UpdateManualControl());
					// driver.readTemperature();
					Thread.sleep(700);  // update every .7 s
				}
			} catch (InterruptedException e) {
			}
		}
	}

	class UpdateThread extends Thread {
		ControlPanelWindow window;

		public UpdateThread(ControlPanelWindow w) {
			super("Control Panel Update Thread");

			window = w;
		}

		public void run() {
			// we'll break on interrupts
			try {
				while (true) {
					try {
						window.updateStatus();
					} catch (AssertionError ae) {
						// probaby disconnected unexpectedly; close window.
						window.dispose();
						break;
					}
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
			}
		}
	}

	public void machineProgress(MachineProgressEvent event) {
	}

	public void machineStateChanged(MachineStateChangeEvent evt) {
		MachineState state = evt.getState();
		// TODO: Do we handle reset correctly?
		if (state.isBuilding() || !state.isConnected()) {
			if (updateThread != null) { updateThread.interrupt(); }
			if (pollThread != null) { pollThread.interrupt(); }
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					dispose();
				}
			});
		}
	}

	public void toolStatusChanged(MachineToolStatusEvent event) {
	}

	public void stateChanged(ChangeEvent e) {
	}
}
