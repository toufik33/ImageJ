import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.io.ObjectInputStream.GetField;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.NumberFormatter;

import sun.nio.ch.SocketOpts.IP;



import ij.plugin.Slicer;
import ij.plugin.StackMaker;
import ij.plugin.frame.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
/**
	Image Processing Demo. Demonstrates how to create a custom user interface, how
	to use ImageJ's ImageProcessor class, and how to run commands in a separate thread.
*/
public class Ics4 extends PlugInFrame implements ActionListener,DialogListener, ChangeListener,  TextListener, ItemListener,PropertyChangeListener {
	 int FPS_MIN = -2100000000;
    int FPS_MAX = 2100000000;
     int FPS_INIT = 200;
    
    
	private Panel panel;
	private int previousID =0;
	private static Frame instance;
	private ImagePlus imp = null;
	private ImageStack ims = null;
	int nombreDeCercles = 3;
	int rayon=5;
	TextField textNombreDeCercles;
	TextField textRayon;
	JSlider slider;
	double sigma=2.0 ;
	TextField textSigma;
	JFormattedTextField txtniv ; 
	ArrayList<Ellipse2D.Double> listCercles = new ArrayList<Ellipse2D.Double>();
	ArrayList<Ellipse2D.Double> listCerclesDiffuse = new ArrayList<Ellipse2D.Double>();
	
	
	
	int height;
	int width;
	int slice= 2;
	int dx =2;
	int dy = 2;
	public Ics4() {
		super("Ics4 Test");
		if (instance!=null) {
			instance.toFront();
			return;
		}
		
		instance = this;
		addKeyListener(IJ.getInstance());
		panel = new Panel();

		addButton("Reset");
		
		addButton("Add Noise");
		addButton("Reduce Noise");
		addButton("Gauss Blur");
		panel.add(new Label("Sigma:"));
		textSigma = new TextField(Double.toString(2.0));
		panel.add(textSigma);
		textSigma.addTextListener(this);
		textSigma.addFocusListener(this);
		addButton("Draw Circles");
		addButton("Random Circles");
		addButton("Diffusion");
		addButton("Cell");
		
		add(panel);
		panel.add(new Label("Nombre de cercle:"));
		textNombreDeCercles = new TextField(Integer.toString(3));
		panel.add(textNombreDeCercles);
		textNombreDeCercles.addTextListener(this);
		textNombreDeCercles.addFocusListener(this);
		panel.add(new Label("Rayon :"));
		textRayon = new TextField(Integer.toString(10));
		panel.add(textRayon);
		textRayon.addTextListener(this);
		textRayon.addFocusListener(this);
		panel.add(new Label("Niveaux:"));
	
		
			slider = new JSlider(JSlider.HORIZONTAL,
                    FPS_MIN, FPS_MAX, FPS_INIT); 
			panel.add(slider,"South");
			
			java.text.NumberFormat numberFormat =
		            java.text.NumberFormat.getIntegerInstance();
		        NumberFormatter formatter = new NumberFormatter(numberFormat);
		        formatter.setMinimum(new Float(FPS_MIN));
		        formatter.setMaximum(new Float(FPS_MAX));
		        txtniv = new JFormattedTextField(formatter);
		        txtniv.setValue(new Float(FPS_INIT));
		    
			panel.add(txtniv);
			
	
			txtniv.addFocusListener(this);
		
			txtniv.addActionListener(this);
			txtniv.addPropertyChangeListener(this);
			slider.addChangeListener(this);
			txtniv.setColumns(10);
			txtniv.getInputMap().put(KeyStroke.getKeyStroke(
		            KeyEvent.VK_ENTER, 0),
		            "check");
			txtniv.getActionMap().put("check", new AbstractAction(){
			public void actionPerformed(ActionEvent e) {

				
				if (!txtniv.isEditValid()) { //The text is invalid.
                    Toolkit.getDefaultToolkit().beep();
                    txtniv.selectAll();
                } else try {                    //The text is valid,
                    txtniv.commitEdit();     //so use it.
                } catch (java.text.ParseException exc) { }
				
			}
		}); 
			
		
		pack();
		GUI.center(this);
		setVisible(true);
		setSize(800, 200);	
	}

	void addButton(String label) {
		Button b = new Button(label);
		b.addActionListener(this);
		b.addKeyListener(IJ.getInstance());
		panel.add(b);
	}

	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		if (e.getID()==WindowEvent.WINDOW_CLOSING) {
			instance = null;	
		}
	}
	
	JLabel label;
	public void stateChanged(ChangeEvent e) {
		double value = slider.getValue();
		txtniv.setText(String.valueOf(slider.getValue()));
		  String str = Double.toString(value);
		  label.setText(str);
		repaint();
	}
	
	public void propertyChange(PropertyChangeEvent e) {
        if ("value".equals(e.getPropertyName())) {
            Number value = (Number)e.getNewValue();
            if (slider != null && value != null) {
                slider.setValue(value.intValue());
            }
        }
    }
	class Runner extends Thread { // inner class
		private String command;
		private ImagePlus imp;
	
		Runner(String command, ImagePlus imp) {
			super(command);
			this.command = command;
			this.imp = imp;
			setPriority(Math.max(getPriority()-2, MIN_PRIORITY));
			start();
			
		}

		public void run() {
			try {
			ImageStack stack = imp.getStack();
			ImageProcessor ip = null;
				//ImageProcessor stack1 = ip.
				runCommand(command, imp ,ip);
			} catch(OutOfMemoryError e) {
				IJ.outOfMemory(command);
				if (imp!=null) imp.unlock();
			} catch(Exception e) {
				CharArrayWriter caw = new CharArrayWriter();
				PrintWriter pw = new PrintWriter(caw);
				e.printStackTrace(pw);
				IJ.log(caw.toString());
				IJ.showStatus("");
				if (imp!=null) imp.unlock();
			}
			
		}
	
		void runCommand(String command, ImagePlus imp, ImageProcessor ip) {
			ImageStack stack = imp.getStack();
			int sli = imp.getImageStackSize();
	for (int n= 1; n<= sli; n++){
			 ip = stack.getProcessor( n);
			
			IJ.showStatus(command + "...");
			long startTime = System.currentTimeMillis();
			Roi roi = imp.getRoi();
			if (command.startsWith("Zoom")||command.equals("Threshold") )
				{roi = null; ip.resetRoi();}
			ImageProcessor mask =  roi!=null?roi.getMask():null;
			if (command.equals("Reset"))
				ip.reset();
			
		
			else if (command.equals("Add Noise"))
				ip.noise(20);
			else if (command.equals("Reduce Noise"))
				ip.medianFilter();
			else if (command.equals("Gauss Blur"))
				ip.blurGaussian(sigma);
			else if (command.equals("Cell"))
			 Cell(ip);
		
			else if (command.equals("Draw Circles"))
				drawCircle(ip);
			else if (command.equals("Random Circles"))
				randomCircle(ip , slice);
			else if (command.equals("Diffusion"))
				//diffusion(imp);
			
			if (mask!=null) ip.reset(mask);
			imp.updateAndDraw();
			imp.unlock();
			IJ.showStatus((System.currentTimeMillis()-startTime)+" milliseconds");
		}
	
		}
		
		public void Cell(ImageProcessor ip){
			int r = 5;
			int x1[]= new int[4];
			int y1[]= new int[4];
			
			ip.translate(2*r+20,0);
			
			
			//for (int i =0 ; i< 60; i++)
			
				
						
						Polygon p = new Polygon(x1, y1, 4);
						x1[0]=-r;
						y1[0]=-r;
						x1[1]=r;
						y1[1]=r;
						x1[2]=r;
						y1[2]=0;
						x1[3]=0;
						y1[3]=r;
						ip.drawPolygon(p);
						//ip.drawLine(15, 10, 100, 50);
				
					
			
			
			
		}

		 public void drawCircle(ImageProcessor ip) {
			IJ.resetEscape();
			int sli = imp.getImageStackSize();
			System.out.println(""+sli);
		   
			
			boolean bool = false;
			Rectangle roi = ip.getRoi();
	        int width = ip.getWidth();
	        int height = ip.getHeight();
	        ArrayList<Ellipse2D.Double> listCercles = new ArrayList<Ellipse2D.Double>();
	        ip.reset();
			double x,y;
			double rayon1 = rayon;
			x = Math.random()*(height-2*rayon1);
			y = Math.random()*(width-2*rayon1);
			Ellipse2D.Double temp;
			boolean ok = false;
			listCercles.add(new Ellipse2D.Double(x,y,2*rayon1,2*rayon1));
			int attempt = 0;
			while ( listCercles.size()<nombreDeCercles) {
				x = Math.random()*(height-2*rayon1);
				y = Math.random()*(width-2*rayon1);
				
			
				temp = new Ellipse2D.Double(x,y,2*rayon1,2*rayon1);
				rayon1 = rayon;
				for (int j=0; j< listCercles.size();j++) {
					if (!temp.intersects(listCercles.get(j).getBounds2D())) {
						ok = true;
					}
					else {
						ok = false;
						break;	
					}
				}
				if (ok)
					listCercles.add(temp);
					
			}

			
			if (imp.getBitDepth()==8 ||imp.getBitDepth()==16 || imp.getBitDepth()==32 ){
				
				ip.reset();
				for (int i=0; i < nombreDeCercles;i++) {
					
			
				
					ip.setColor(slider.getValue());
					System.out.println(""+sli);
					//for (int n= 1; n<= sli; n++){
					//ImageStack stack = imp.getStack();
					ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
				//}
					
					
				}}
			
			
		}
	
		void randomCircle(ImageProcessor ip, int slices) {
			IJ.resetEscape();
			
			ImageStack stack = imp.getStack();
			
			boolean bool = false;
			Rectangle roi = stack.getRoi();
	        int width = stack.getWidth();
	        int height = stack.getHeight();
	
	        ArrayList<Ellipse2D.Double> listCercles = new ArrayList<Ellipse2D.Double>();
		
			double x,y;

			//double rayon = 15;
			double rayon1 =  Math.random()* rayon;
		
			x = Math.random()*(height-2*rayon1);
			y = Math.random()*(width-2*rayon1);
			Ellipse2D.Double temp;
			boolean ok = false;
			listCercles.add(new Ellipse2D.Double(x,y,2*rayon1,2*rayon1));
			int attempt = 0;
			while ( listCercles.size()<nombreDeCercles) {
				x = Math.random()*(height-2*rayon1);
				y = Math.random()*(width-2*rayon1);
				
				
				temp = new Ellipse2D.Double(x,y,2*rayon1,2*rayon1);
				rayon1 =  Math.random()*(rayon);
				for (int j=0; j< listCercles.size();j++) {
					if (!temp.intersects(listCercles.get(j).getBounds2D())) {
						ok = true;
					}
					else {
						ok = false;
						break;	
					}
				}
				if (ok)
					listCercles.add(temp);
					
			}
			if (attempt++>=10000) {
				JOptionPane.showInputDialog(this,"Trop de tentatives sans trouver de solutions, changez les parametres.");
				bool = true;
			}
		
			if (ip  instanceof ByteProcessor){
				
			for (int i=0; i < nombreDeCercles;i++) {
				ip.setColor(slider.getValue());
				ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
				}
				}
			if (ip instanceof ShortProcessor){
				for (int i=0; i < nombreDeCercles;i++) {
				ip.setColor(slider.getValue());
				ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
				}
				}
			if (ip instanceof FloatProcessor){
				
				for (int i=0; i < nombreDeCercles;i++) {
				ip.setValue(slider.getValue());
				ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
				}
				}
		}
	} // Runner inner class
	
	
	@Override
	public void itemStateChanged(ItemEvent arg0) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void textValueChanged(TextEvent e) {
		sigma = Double.parseDouble(textSigma.getText());
		nombreDeCercles = Integer.parseInt(textNombreDeCercles.getText());
		rayon = Integer.parseInt(textRayon.getText());
		
	}
	
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public void actionPerformed(ActionEvent e) {
	
	ImagePlus imp = IJ.getImage();

		
		
		if (imp==null) {
			IJ.beep();
			IJ.showStatus("No image");
			previousID = 0;
			return;
		}
		if (!imp.lock())
			{previousID = 0; return;}
		ImageProcessor ip = imp.getProcessor();
		int id = imp.getID();
		if (id!=previousID)
			ip.snapshot();
		previousID = id;
		String label = e.getActionCommand();
		if (label==null)
			return;
		new Runner(label, imp);
		}

	
	
} 
