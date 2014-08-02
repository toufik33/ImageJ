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
/**
	Image Processing Demo. Demonstrates how to create a custom user interface, how
	to use ImageJ's ImageProcessor class, and how to run commands in a separate thread.
*/
public class IcsStack3 extends PlugInFrame implements ActionListener,DialogListener, ChangeListener,  TextListener, ItemListener,PropertyChangeListener {
	static final int FPS_MIN = -2100000000;
    static final int FPS_MAX = 2100000000;
    static final int FPS_INIT = 200; 
	private Panel panel;
	private int previousID;
	private static Frame instance;
	//int niveau1=0;
	private ImagePlus imp = null;
	private ImageStack ims = null;
	int nombreDeCercles = 20;
	int rayon= 30;
	TextField textNombreDeCercles;
	TextField textRayon;
	JSlider slider;
	double sigma=2.0 ;
	JFormattedTextField txtniv ; //= new TextField(Double.toString(10));
	ArrayList<Ellipse2D.Double> listCercles = new ArrayList<Ellipse2D.Double>();
	ArrayList<Ellipse2D.Double> listCerclesDiffuse = new ArrayList<Ellipse2D.Double>();
	//private final int flags= DOES_ALL|FINAL_PROCESSING|CONVERT_TO_FLOAT|PARALLELIZE_STACKS|KEEP_PREVIEW;
	int height;
	int width;
	int slice= 2;
	int dx =2;
	int dy = 2;
	public IcsStack3() {
		super("Ics Stack");
		if (instance!=null) {
			instance.toFront();
			return;
		}
		
		instance = this;
		addKeyListener(IJ.getInstance());

		//setLayout(new FlowLayout());
		panel = new Panel();
		//
		//panel.setLayout(new GridLayout(4, 4, 5, 5));
		addButton("Reset");
		addButton("Flip");
		addButton("Invert");
		addButton("Rotate");
		addButton("Lighten");
		addButton("Darken");
		addButton("Zoom In");
		addButton("Zoom Out");
		addButton("Smooth");
		addButton("Sharpen");
		addButton("Find Edges");
		addButton("Threshold");
		addButton("Add Noise");
		addButton("Reduce Noise");
		addButton("Gauss Blur");
		addButton("Draw Circles");
		addButton("Random Circles");
		addButton("Diffusion");
		add(panel);
		panel.add(new Label("Nombre de cercle:"));
		textNombreDeCercles = new TextField(Integer.toString(1));
		panel.add(textNombreDeCercles);
		textNombreDeCercles.addTextListener(this);
		textNombreDeCercles.addFocusListener(this);
		panel.add(new Label("Rayon :"));
		textRayon = new TextField(Integer.toString(10));
		panel.add(textRayon);
		textRayon.addTextListener(this);
		textRayon.addFocusListener(this);
		panel.add(new Label("Niveaux:"));
		
		
		
		//ImageProcessor ip = null;
		//if (ip instanceof ByteProcessor){
		//TextField txtniv = new TextField(Double.toString(10));
		
			slider = new JSlider(JSlider.HORIZONTAL,
                    FPS_MIN, FPS_MAX, FPS_INIT); 
			panel.add(slider,"South");
			
			java.text.NumberFormat numberFormat =
		            java.text.NumberFormat.getIntegerInstance();
		        NumberFormatter formatter = new NumberFormatter(numberFormat);
		        formatter.setMinimum(new Integer(FPS_MIN));
		        formatter.setMaximum(new Integer(FPS_MAX));
		        txtniv = new JFormattedTextField(formatter);
		        txtniv.setValue(new Integer(FPS_INIT));
		       // textField.setColumns(5); //get some space
		       // textField.addPropertyChangeListener(this);
			//txtniv = new JFormattedTextField(Double.toString(10));
			panel.add(txtniv);
			
			//txtniv.setText("0");
			//txtniv.addTextListener(this);
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
				runCommand(command, imp);
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
	
		void runCommand(String command, ImagePlus imp) {
			ImageStack stack = imp.getStack();
			int sli = imp.getImageStackSize();
	for (int n= 1; n<= sli; n++){
			ImageProcessor ip = stack.getProcessor( n);
			
			IJ.showStatus(command + "...");
			long startTime = System.currentTimeMillis();
			Roi roi = imp.getRoi();
			if (command.startsWith("Zoom")||command.equals("Threshold"))
				{roi = null; ip.resetRoi();}
			ImageProcessor mask =  roi!=null?roi.getMask():null;
			if (command.equals("Reset"))
				ip.reset();
			else if (command.equals("Flip"))
				ip.flipVertical();
			else if (command.equals("Invert"))
				ip.invert();
			else if (command.equals("Lighten")) {
				if (imp.isInvertedLut())
					ip.multiply(0.9);
				else
					ip.multiply(1.1);
			}
			else if (command.equals("Darken")) {
				if (imp.isInvertedLut())
					ip.multiply(1.1);
				else
					ip.multiply(0.9);
			}
			else if (command.equals("Rotate"))
				ip.rotate(30);
			else if (command.equals("Zoom In"))
				ip.scale(1.2, 1.2);
			else if (command.equals("Zoom Out"))
				ip.scale(.8, .8);
			else if (command.equals("Threshold"))
				ip.autoThreshold();
			else if (command.equals("Smooth"))
				ip.smooth();
			else if (command.equals("Sharpen"))
				ip.sharpen();
			else if (command.equals("Find Edges"))
				ip.findEdges();
			else if (command.equals("Add Noise"))
				ip.noise(20);
			else if (command.equals("Reduce Noise"))
				ip.medianFilter();
			//else if (command.equals("Gauss Blur"))
				//ip.blurGaussian(sigma);
		
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
		
		/*void deplacer (int dx, int dy){
			int x= getX()+ dx;
			int y= getY()+dy;
			
			System.out.println("ok cool"+x+";"+y);
		}*/
		void diffusion (ImagePlus imp){
			ImageStack stack = imp.getStack();
			int sli = imp.getImageStackSize();
	for (int n= 1; n<= sli; n++){
			ImageProcessor ip = stack.getProcessor( n);
			
				
				System.out.println(""+sli);
			//ImageProcessor ip = stack.getProcessor( n);
				//for (int n= 1; n<= sli; n++){
				//ImageProcessor ip = stack.getProcessor( sli);
				//ImageStack stack = imp.getStack();
				int	niveau =0;
				boolean bool = false;
				Rectangle roi = ip.getRoi();
		        int width = ip.getWidth();
		        int height = ip.getHeight();
		       // float[] pixels = (float[])ip.getPixels();
		        
		        ArrayList<Ellipse2D.Double> listCerclesDiffuse = new ArrayList<Ellipse2D.Double>();
		        listCerclesDiffuse.addAll(listCercles);
		        for(Ellipse2D.Double elem: listCerclesDiffuse)
			       {
			       	 System.out.println (elem);
			       }
				//int nombreDeCercles = 25;
				//double x,y;
				//x = Math.random()*(height-2*rayon);
				//y = Math.random()*(width-2*rayon);
				//Ellipse2D.Double temp;
				//boolean ok = false;
				//listCercles.add(new Ellipse2D.Double(x,y,2*rayon,2*rayon));
				//int attempt = 0;
				//while ( listCerclesDiffuse.size()<nombreDeCercles) {
					//x = Math.random()*(height-2*rayon);
					//y = Math.random()*(width-2*rayon);
					
					//rayon = 30;
					//temp = new Ellipse2D.Double(x,y,2*rayon,2*rayon);
					//rayon =  Math.random()*(30);
					//for (int j=0; j< listCercles.size();j++) {
						//if (!temp.intersects(listCercles.get(j).getBounds2D())) {
						//	ok = true;
					//	}
						//else {
							//ok = false;
							//break;	
						//}
					//}
					//if (ok)
						//listCercles.add(temp);
						
				//}
				if (imp.getBitDepth()==8){
					//ImageProcessor ip = stack.getProcessor(sli);
					for (int i=0; i < nombreDeCercles;i++) {
				//int niveau1=1+(int) (Math.random()*254);
				//ip.setColor(/*slide.getValue()*/niveau1);
						//ArrayList<Ellipse2D.Double> listCerclesDiffuse = new ArrayList<Ellipse2D.Double>();
						listCerclesDiffuse.addAll(listCercles);
						for(Ellipse2D.Double elem: listCerclesDiffuse)
					       {
					       	 System.out.println (elem);
					       }
				ip.setColor(slider.getValue());
			ip.fillOval((int)listCerclesDiffuse.get(i).getX()+dx,(int)listCerclesDiffuse.get(i).getY()+dy,(int)listCerclesDiffuse.get(i).getWidth(),(int)listCerclesDiffuse.get(i).getHeight());
				}
			
				}}
		}
		void drawCircle(ImageProcessor ip) {
			IJ.resetEscape();
			int sli = imp.getImageStackSize();
			System.out.println(""+sli);
		    //ImageProcessor ip = stack.getProcessor( n);
			//for (int n= 1; n<= sli; n++){
			//ImageProcessor ip = stack.getProcessor( sli);
			//ImageStack stack = imp.getStack();
			int	niveau =0;
			boolean bool = false;
			Rectangle roi = ip.getRoi();
	        int width = ip.getWidth();
	        int height = ip.getHeight();
	       // float[] pixels = (float[])ip.getPixels();
	        //ArrayList<Ellipse2D.Double> listCercles = new ArrayList<Ellipse2D.Double>();
			//int nombreDeCercles = 25;
			double x,y;
			x = Math.random()*(height-2*rayon);
			y = Math.random()*(width-2*rayon);
			Ellipse2D.Double temp;
			boolean ok = false;
			listCercles.add(new Ellipse2D.Double(x,y,2*rayon,2*rayon));
			int attempt = 0;
			while ( listCercles.size()<nombreDeCercles) {
				x = Math.random()*(height-2*rayon);
				y = Math.random()*(width-2*rayon);
				
				//rayon = 30;
				temp = new Ellipse2D.Double(x,y,2*rayon,2*rayon);
				//rayon =  Math.random()*(30);
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

			
			if (imp.getBitDepth()==8){
				//ImageProcessor ip = stack.getProcessor(sli);
				ip.reset();
				for (int i=0; i < nombreDeCercles;i++) {
			//int niveau1=1+(int) (Math.random()*254);
			//ip.setColor(/*slide.getValue()*/niveau1);
				//ArrayList<Ellipse2D.Double> listCercles = new ArrayList<Ellipse2D.Double>();
					for(Ellipse2D.Double elem: listCercles)
					{
			       	 System.out.println (elem);
					}
				//ArrayList<Ellipse2D.Double> listCerclesDiffuse = new ArrayList<Ellipse2D.Double>();
				listCerclesDiffuse.addAll(listCercles);
					for(Ellipse2D.Double elem: listCerclesDiffuse)
					{
			       	 System.out.println (elem);
					}
					ip.setColor(slider.getValue());
					ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
				}
			}
			if (imp.getBitDepth()==16){
				//ImageProcessor ip = stack.getProcessor(sli);
				for (int i=0; i < nombreDeCercles;i++) {
				//int niveau=(int) (Math.random()*65535);
				
				//ip.setColor(/*slide.getValue()*/niveau2);
				
				ip.setColor(slider.getValue());
				ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
				}
				}
			if (imp.getBitDepth()==32){
				//ImageProcessor ip = stack.getProcessor(sli);
				for (int i=0; i < nombreDeCercles;i++) {
				//double niveau1=(double) (Math.random()*210000000);
				//ip.setValue(/*slide.getValue()*/niveau3);
				ip.setValue(slider.getValue());
//				double niveau3;
				//int sli = imp.getImageStackSize();
				System.out.println(""+sli);
				for (int n= 1; n<= sli; n++){
				ImageStack stack = imp.getStack();
				
				ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
				}}
				}
		}//}
	
		void randomCircle(ImageProcessor ip, int slices) {
			//IJ.resetEscape();
			ImageStack stack = imp.getStack();
			//int slicess = ip.getSliceNumber();
			boolean bool = false;
			Rectangle roi = stack.getRoi();
	        int width = stack.getWidth();
	        int height = stack.getHeight();
	       // float[] pixels = (float[])ip.getPixels();
	        ArrayList<Ellipse2D.Double> listCercles = new ArrayList<Ellipse2D.Double>();
			//int nombreDeCercles = 25;
			double x,y;

			double rayon = 15;
			rayon =  Math.random()* 20;
			//rayon = 30;
			x = Math.random()*(height-2*rayon);
			y = Math.random()*(width-2*rayon);
			Ellipse2D.Double temp;
			boolean ok = false;
			listCercles.add(new Ellipse2D.Double(x,y,2*rayon,2*rayon));
			int attempt = 0;
			while ( listCercles.size()<nombreDeCercles) {
				x = Math.random()*(height-2*rayon);
				y = Math.random()*(width-2*rayon);
				
				//rayon = 30;
				temp = new Ellipse2D.Double(x,y,2*rayon,2*rayon);
				rayon =  Math.random()*(30);
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
			//int niveau1=1+(int) (Math.random()*254);
			//ip.setColor(/*slide.getValue()*/niveau1);
			
			ip.setColor(slider.getValue());
			ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
			}
			}
			if (ip instanceof ShortProcessor){
				for (int i=0; i < nombreDeCercles;i++) {
				//int niveau2=(int) (Math.random()*65535);
				//ip.setColor(/*slide.getValue()*/niveau2);
				
				ip.setColor(slider.getValue());
				ip.fillOval((int)listCercles.get(i).getX(),(int)listCercles.get(i).getY(),(int)listCercles.get(i).getWidth(),(int)listCercles.get(i).getHeight());
				}
				}
			if (ip instanceof FloatProcessor){
				for (int i=0; i < nombreDeCercles;i++) {
				//double niveau3=(double) (Math.random()*210000000);
				//ip.setValue(/*slide.getValue()*/niveau3);
				ip.setValue(slider.getValue());
//				double niveau3;
				
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
			return;}
		
		if (!imp.lock())
			{previousID = 0; return;}
		int id = imp.getID();

		if (id!=previousID)
			imp.getProcessor().snapshot();
		previousID = id;
		String label = e.getActionCommand();
		
		if (label==null)
			return;
		new Runner(label, imp);
		}

	
	
} 
