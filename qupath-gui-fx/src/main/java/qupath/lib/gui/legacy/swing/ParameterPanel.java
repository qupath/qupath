/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.legacy.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.plugins.parameters.BooleanParameter;
import qupath.lib.plugins.parameters.ChoiceParameter;
import qupath.lib.plugins.parameters.DoubleParameter;
import qupath.lib.plugins.parameters.EmptyParameter;
import qupath.lib.plugins.parameters.IntParameter;
import qupath.lib.plugins.parameters.NumericParameter;
import qupath.lib.plugins.parameters.Parameter;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.parameters.StringParameter;

/**
 * A panel for displaying a list of parameters suitably to aid with creating Swing GUIs.
 * 
 * @author Pete Bankhead
 *
 */
public class ParameterPanel extends JPanel implements Scrollable {
	
	private static final long serialVersionUID = 1L;
	
	private final static Logger logger = LoggerFactory.getLogger(ParameterPanel.class);
	
	private Vector<ParameterChangeListener> listeners = new Vector<>();
	private GridBagLayout layout = new GridBagLayout();
	private GridBagConstraints constraints = null;
	private ParameterList params;
	private Map<Parameter<?>, JComponent> map = new HashMap<Parameter<?>, JComponent>();
	
	public ParameterPanel(ParameterList params) {
		super();
		this.setLayout(layout);
		this.params = params;
		initialize();
		setMinimumSize(getPreferredSize());
	}
	
	public ParameterList getParameters() {
		return params;
	}
	
	private void initialize() {
//		for (Parameter<?> p : params.getParameterList()) {
		for (Entry<String, Parameter<?>> entry : params.getParameters().entrySet()) {
			Parameter<?> p = entry.getValue();
			// Don't show hidden parameters
			if (p.isHidden())
				continue;
			if (p instanceof DoubleParameter)
				addDoubleParameter((DoubleParameter)p);
			else if (p instanceof IntParameter)
				addIntParameter((IntParameter)p);
			else if (p instanceof StringParameter)
				addStringParameter((StringParameter)p);
			else if (p instanceof EmptyParameter)
				addEmptyParameter((EmptyParameter)p);
			else if (p instanceof ChoiceParameter)
				addChoiceParameter((ChoiceParameter)p);
			else if (p instanceof BooleanParameter) {
				addBooleanParameter((BooleanParameter)p);
			}
//			// Don't show hidden parameters
//			if (p.isHidden())
//				map.get(p).setVisible(false);
		}
//		// Testing showing / hiding parameters
//		addParameterChangeListener(new ParameterChangeListener() {
//
//			@Override
//			public void parameterChanged(ParameterList parameterList, String key) {
//				Parameter<?> p = parameterList.getParameters().get(key);
//				if (!(p instanceof BooleanParameter))
//					return;
//				boolean value = parameterList.getBooleanParameterValue(key);
//				for (Parameter<?> p2 : parameterList.getParameters().values()) {
//					if (p2 instanceof NumericParameter<?>) {
//						p2.setHidden(value);
//						map.get(p2).setVisible(value);
//					}
//				}
//				invalidate();
//			}
//			
//		});
	}
	
	
//	private void initialize() {
////		for (Parameter<?> p : params.getParameterList()) {
//		for (Entry<String, Parameter<?>> entry : params.getParameters().entrySet()) {
//			Parameter<?> p = entry.getValue();
//			// Don't show hidden parameters
//			if (p.isHidden())
//				continue;
//			if (p instanceof DoubleParameter)
//				addDoubleParameter((DoubleParameter)p);
//			else if (p instanceof IntParameter)
//				addIntParameter((IntParameter)p);
//			else if (p instanceof StringParameter)
//				addStringParameter((StringParameter)p);
//			else if (p instanceof EmptyParameter)
//				addEmptyParameter((EmptyParameter)p);
//			else if (p instanceof ChoiceParameter)
//				addChoiceParameter((ChoiceParameter<Object>)p);
//			else if (p instanceof BooleanParameter)
//				addBooleanParameter((BooleanParameter)p);
//		}
//	}
	
	
	public void addParameterChangeListener(ParameterChangeListener listener) {
		listeners.add(listener);
	}

	public void removeParameterChangeListener(ParameterChangeListener listener) {
		listeners.remove(listener);
	}
	
	
	private String getKey(Parameter<?> param) {
		// This is a rather clumsy workaround as the keys are not currently stored here...
		for (Entry<String, Parameter<?>> entry : params.getParameters().entrySet()) {
			if (entry.getValue().equals(param))
				return entry.getKey();
		}
		return null;
	}
	
	
	private void fireParameterChangedEvent(Parameter<?> param, boolean isAdjusting) {
		String key = getKey(param);
		if (key != null) {
			for (ParameterChangeListener listener: listeners) {
				listener.parameterChanged(params, key, isAdjusting);
			}
		}
	}

	private void addBooleanParameter(BooleanParameter param) {
		addCheckBoxParameter(param);
	}
	
	private void addDoubleParameter(DoubleParameter param) {
		if (param.hasLowerAndUpperBounds())
			addSliderParameter(param);
		else
			addNumericTextField(param);
	}

	private void addIntParameter(IntParameter param) {
		if (param.hasLowerAndUpperBounds())
			addSliderParameter(param);
		else
			addNumericTextField(param);
	}
	
	private void addNumericTextField(NumericParameter<? extends Number> param) {
		JTextField tf = getTextField(param, 8);
		if (param.getUnit() != null) {
			JPanel panel = new JPanel();
			panel.add(tf);
			panel.add(new JLabel(param.getUnit()));
			addParamComponent(param, param.getPrompt(), panel);
		} else
			addParamComponent(param, param.getPrompt(), tf);
	}

	private void addStringParameter(StringParameter param) {
		addParamComponent(param, param.getPrompt(), getTextField(param, 25));
	}

	private void addEmptyParameter(EmptyParameter param) {
		JLabel label = new JLabel(param.getPrompt());
		if (param.isTitle()) {
			label.setFont(label.getFont().deriveFont(Font.BOLD));
			label.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
		}
		addParamComponent(param, null, label);
	}

	private void addChoiceParameter(ChoiceParameter<Object> param) {
		JComboBox<Object> combo = new JComboBox<>(new Vector<>(param.getChoices()));
		combo.setSelectedItem(param.getValueOrDefault());
		combo.addActionListener(new ParameterComboActionListener(combo, param));
		addParamComponent(param, param.getPrompt(), combo);
	}
	
	private void addCheckBoxParameter(BooleanParameter param) {
		JCheckBox cb = new JCheckBox(param.getPrompt(), param.getValueOrDefault());
		cb.addChangeListener(new ParameterCheckBoxChangeListener(cb, param));
		addParamComponent(param, null, cb);
	}
	
	private void addSliderParameter(IntParameter param) {
		int min = (int)param.getLowerBound();
		int max = (int)(param.getUpperBound() + .5);
		JSlider slider = new JSlider(min, max, param.getValueOrDefault());
		JTextField tf = new JTextField(6);
		tf.setEditable(false);
		tf.setText(""+slider.getValue());
		slider.addChangeListener(new ParameterSliderChangeListener(slider, param, param.getLowerBound(), param.getUpperBound(), tf));
//		JPanel panel = new JPanel();
//		panel.add(slider);
//		panel.add(tf);
		slider.setMinimumSize(new Dimension(slider.getPreferredSize().width, slider.getMinimumSize().height));
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(slider, BorderLayout.CENTER);
		panel.add(tf, BorderLayout.EAST);
		addParamComponent(param, param.getPrompt(), panel);
	}
	
	
	private void addSliderParameter(DoubleParameter param) {
		double lower = param.getLowerBound();
		double higher = param.getUpperBound();
		int sliderMax;
		// Choose a sensible increment
		if (higher - lower > 1000)
			sliderMax = (int)Math.round((higher - lower));
		else if (higher - lower > 100)
			sliderMax = (int)Math.round((higher - lower) * 10);
		else
			sliderMax = (int)Math.round((higher - lower) * 100);
		int intValue = (int)((param.getValueOrDefault() - lower) / (higher - lower) * sliderMax + .5);
		final JSlider slider = new JSlider(0, sliderMax,  Math.min(Math.max(0, intValue), sliderMax));
		JTextField tf = new JTextField(6);
		setTextFieldFromNumber(tf, param.getValueOrDefault(), param.getUnit());
		tf.setEditable(false);
		slider.addChangeListener(new ParameterSliderChangeListener(slider, param, lower, higher, tf));
//		JPanel panel = new JPanel();
//		panel.add(slider);
//		panel.add(tf);
		slider.setMinimumSize(new Dimension(slider.getPreferredSize().width, slider.getMinimumSize().height));
//		slider.setMinimumSize(new Dimension(slider.getPreferredSize()));
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(slider, BorderLayout.CENTER);
		panel.add(tf, BorderLayout.EAST);
		addParamComponent(param, param.getPrompt(), panel);
	}
	
	
	protected static void setTextFieldFromNumber(JTextComponent text, Number value, String unit) {
		String s;
		if (value == null)
			s = "";
		else  {
			if ((value instanceof Double) || (value instanceof Float))
				s = String.format("%.2f", value.doubleValue());
			else
				s = String.format("%d", value.longValue());
			if (unit != null)
				s += (" " + unit);
		}
		// Only set the text if it's different - avoids some exceptions due to the complex interplay between listeners...
		if (!text.getText().equals(s))
			text.setText(s);
	}
	
	
	protected JTextField getTextField(Parameter<?> param, int cols) {
		JTextField tf = new JTextField(cols);
		Object defaultVal = param.getValueOrDefault();
		if (defaultVal != null)
			tf.setText(defaultVal.toString());
//		tf.addActionListener(new ParameterActionListener(tf, param));
		tf.addKeyListener(new ParameterKeyListener(tf, param));
		return tf;
	}
	
	
	// GridBagLayout version
	private void addParamComponent(Parameter<?> parameter, String text, JComponent component) {
		map.put(parameter, component);
		String help = parameter.getHelpText();
		if (help != null) {
			for (Component child : component.getComponents()) {
				if (child instanceof JComponent) {
					((JComponent)child).setToolTipText(help);
				}
			}
			component.setToolTipText(help);
		}
		if (constraints == null) {
			constraints = new GridBagConstraints();
			constraints.gridwidth = 1;
			constraints.gridheight = 1;
			constraints.gridy = 0;
		}
		constraints.gridy++;
		constraints.gridx = 0;
		if (text == null) {
			constraints.gridwidth = 2;
			constraints.weightx = 1.0;
			constraints.anchor = GridBagConstraints.CENTER;
			add(component, constraints);
		} else {
			constraints.weightx = 0;
			constraints.gridwidth = 1;
			constraints.anchor = GridBagConstraints.EAST;
			JLabel label = new JLabel(text);
			if (help != null)
				label.setToolTipText(help);
			add(label, constraints);
			label.setLabelFor(component);
			constraints.gridx++;
			constraints.weightx = 1.0;
			constraints.anchor = GridBagConstraints.WEST;
//			component.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
			add(component, constraints);			
		}
	}
	
	public boolean getParameterEnabled(String key) {
		return getParameterEnabled(params.getParameters().get(key));
	}
	
	public boolean getParameterEnabled(Parameter<?> param) {
		JComponent comp = map.get(param);
		return comp != null && comp.isEnabled();
	}
	
	public void setParameterEnabled(String key, boolean enabled) {
		setParameterEnabled(params.getParameters().get(key), enabled);
	}
	
	
	public void setParameterEnabled(Parameter<?> param, boolean enabled) {
		JComponent comp = map.get(param);
		if (comp != null)
			setEnabledRecursively(comp, enabled);
	}
	
	private void setEnabledRecursively(Component comp, boolean enabled) {
		comp.setEnabled(enabled);
		if (comp instanceof JComponent) {
			for (Component child : ((JComponent)comp).getComponents())
				setEnabledRecursively(child, enabled);		
		}
	}
	
	
	class ParameterComboActionListener implements ActionListener {
		
		private JComboBox<? extends Object> combo;
		private Parameter<Object> param;
		
		public ParameterComboActionListener(JComboBox<Object> combo, Parameter<Object> param) {
			this.combo = combo;
			this.param = param;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (param.setValue(combo.getSelectedItem()))
				fireParameterChangedEvent(param, false);
		}

		
	}

	class ParameterKeyListener implements KeyListener {
		
		private JTextField textField;
		private Parameter<?> param;
		
		public ParameterKeyListener(JTextField textField, Parameter<?> param) {
			this.textField = textField;
			this.param = param;
		}

		@Override
		public void keyTyped(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
		}

		@Override
		public void keyReleased(KeyEvent e) {
			if (param.setStringLastValue(Locale.getDefault(), textField.getText()))
				fireParameterChangedEvent(param, false);
		}

	}

	
	class ParameterCheckBoxChangeListener implements ChangeListener {
		
		private JCheckBox cb;
		private BooleanParameter param;
		
		public ParameterCheckBoxChangeListener(JCheckBox cb, BooleanParameter param) {
			this.cb = cb;
			this.param = param;
		}

		@Override
		public void stateChanged(ChangeEvent e) {
			if (param.setValue(cb.isSelected()))
				fireParameterChangedEvent(param, false);
		}
		
	}

//	class ParameterSliderChangeListener implements ChangeListener {
//		
//		private double min, max;
//		private JSlider slider;
//		private NumericParameter<?> param;
//		private JTextComponent text;
//		
//		public ParameterSliderChangeListener(JSlider slider, NumericParameter<?> param, double min, double max, JTextComponent text) {
//			this.slider = slider;
//			this.param = param;
//			this.min = min;
//			this.max = max;
//			this.text = text;
//		}
//
//		@Override
//		public void stateChanged(ChangeEvent e) {
//			double val = (double)(slider.getValue() - slider.getMinimum()) / slider.getMaximum() * max + min;
//			if (param.setDoubleLastValue(val)) {
//				if (text != null) {
//					setTextFieldFromNumber(text, param.getValueOrDefault(), param.getUnit());
//				}
//				fireParameterChangedEvent(param);
//			}
//		}
//		
//	}
	
	
	class ParameterSliderChangeListener implements ChangeListener, DocumentListener {
		
		private double min, max;
		private JSlider slider;
		private NumericParameter<?> param;
		private JTextComponent text;
		
		private boolean sliderChanging = false;
		private boolean textChanging = false;
		
		public ParameterSliderChangeListener(JSlider slider, NumericParameter<?> param, double min, double max, JTextComponent text) {
			this.slider = slider;
			this.param = param;
			this.min = min;
			this.max = max;
			this.text = text;
			
			this.text.setEditable(true);
			this.text.getDocument().addDocumentListener(this);
		}

		@Override
		public void stateChanged(ChangeEvent e) {
			if (textChanging)
				return;
			sliderChanging = true;
			double val = (double)(slider.getValue() - slider.getMinimum()) / slider.getMaximum() * max + min;
			if (param.setDoubleLastValue(val)) {
				if (text != null) {
					setTextFieldFromNumber(text, param.getValueOrDefault(), param.getUnit());
				}
				fireParameterChangedEvent(param, slider.getValueIsAdjusting());
			}
			sliderChanging = false;
		}

		@Override
		public void insertUpdate(DocumentEvent e) {
			handleTextUpdate();
		}

		@Override
		public void removeUpdate(DocumentEvent e) {
			handleTextUpdate();
		}

		@Override
		public void changedUpdate(DocumentEvent e) {
			handleTextUpdate();
		}
		
		void handleTextUpdate() {
			if (sliderChanging)
				return;
			String s = text.getText();
			if (s == null || s.trim().length() == 0)
				return;
//			System.out.println("Text: " + s);
			try {
				String unit = param.getUnit();
				if (unit != null)
					s = s.toLowerCase().replace(unit.toLowerCase(), "").trim();
				if (s.length() == 0)
					return;
				double val = Double.parseDouble(s);
				double previousValue = param.getValueOrDefault().doubleValue();
				if (Double.isNaN(val) || val == previousValue)
					return;
				
				int index = (int)((val - param.getLowerBound()) / (param.getUpperBound() - param.getLowerBound()) * slider.getMaximum() + .5);
				if (index < 0)
					index = 0;
				if (index > slider.getMaximum())
					index = slider.getMaximum();
				
				textChanging = true;
				param.setDoubleLastValue(val);
				slider.setValue(index);
				fireParameterChangedEvent(param, slider.getValueIsAdjusting());
				textChanging = false;
			} catch (Exception e) {
				logger.debug("Cannot parse number from {} - will keep default of {}", s, param.getValueOrDefault());
//				e.printStackTrace();
			} finally {
				textChanging = false;
			};
		}
		
	}
	
	
	
	static void demoParameterPanel() {
		JFrame frame = new JFrame("Testing parameter panel");
		int k = 0;
		final ParameterList params = new ParameterList().
				addTitleParameter("Parameter list").
				addEmptyParameter("Here is a list of parameters that I am testing out").
				addIntParameter(Integer.toString(k++), "Enter an int", 5, "px", "Unbounded int").
				addDoubleParameter(Integer.toString(k++), "Enter a double", 5.2, "microns", "Unbounded double").
				addDoubleParameter(Integer.toString(k++), "Enter a double in range", 5.2, null, 1, 10, "Bounded double").
				addIntParameter(Integer.toString(k++), "Enter an int in range", 5, null, 1, 10, "Bounded int").
				addStringParameter(Integer.toString(k++), "Enter a string", "Default here").
				addChoiceParameter(Integer.toString(k++), "Choose a choice", "Two", Arrays.asList("One", "Two", "Three"), "Simple choice").
				addChoiceParameter(Integer.toString(k++), "Choose a number choice", Integer.valueOf(2), Arrays.asList(1, 2, 3), "Numeric choice").
				addBooleanParameter(Integer.toString(k++), "Check me out", true);
		
		
		
		frame.setLayout(new BorderLayout());
		ParameterPanel panel = new ParameterPanel(params);
		
		final JTextArea textArea = new JTextArea();
		for (Parameter<?> p : params.getParameters().values()) {
			textArea.append(p + "\n");
		}
		panel.addParameterChangeListener(new ParameterChangeListener() {
			@Override
			public void parameterChanged(ParameterList params, String key, boolean isAdjusting) {
				textArea.setText("");
				for (Parameter<?> p : params.getParameters().values())
					textArea.append(p.toString() + "\n");
			}
		});
		
		JScrollPane scroll = new JScrollPane(panel);
		scroll.setBackground(panel.getBackground());
		scroll.setForeground(panel.getForeground());
		frame.add(scroll, BorderLayout.CENTER);
		frame.add(new JScrollPane(textArea), BorderLayout.SOUTH);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		invalidate();
		validate();
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return 5;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		return 5;
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return false;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
	
	
	/**
	 * Set a numeric parameter value (either int or double).
	 * 
	 * The reason for using this method rather than setting the parameter value directly is that it ensures that
	 * any displayed components (text fields, sliders...) are updated accordingly.
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public boolean setNumericParameterValue(final String key, Number value) {
		// Try to get a component to set
		Parameter<?> parameterOrig = params.getParameters().get(key);
		if (parameterOrig == null || !(parameterOrig instanceof NumericParameter)) {
			logger.warn("Unable to set parameter {} with value {} - no numeric parameter found with that key", key, value);
			return false;			
		}
		NumericParameter<?> parameter = (NumericParameter<?>)parameterOrig;
		JComponent component = map.get(parameter);
		// Occurs with hidden parameters
		if (component == null) {
			parameter.setDoubleLastValue(value.doubleValue());
			return true;
		}
		for (Component comp : component.getComponents()) {
			if (comp instanceof JTextField) {
				// Only change the text if necessary
				JTextField textField = (JTextField)comp;
				setTextFieldFromNumber(textField, value, parameter.getUnit());
				return true;
			}
		}
		logger.warn("Unable to set parameter {} with value {} - no component found", key, value);		
		return false;
	}
	
	
}
