import com.cycling74.max.*;
import com.cycling74.msp.*;
import java.lang.Math;

public class resultSpeed2 extends MSPPerformer
{
	private int _buffersize1;
	private float _maxdelay;
	private float[] _buffer;
	private int[] _cur;
	private float[] _xspeaker;
	private float[] _yspeaker;
	private float[] _xListening;
	private float[] _yListening;
	private double _fech;
	private float _c=340.0f;
	private int _nspk;
	private int _npoints;
	private double[] _R;
	private double[] _coefs;
	private int[] _dint;
	private float _xdelta;
	private float _ydelta;



	private static final String[] INLET_ASSIST = new String[]{
		"input (sig)", "input angle [0..1] (sig)"
    };
	private static final String[] OUTLET_ASSIST = new String[]{
		"speaker output (sig)"
	};
	
// DEF
	
	public resultSpeed2(int nspk)
	{
		int[] ins,outs;
		int i;

		_nspk=nspk;
		_npoints=3;

		ins = new int[_nspk];
		for (i=0; i<_nspk; i++){ ins[i]=SIGNAL;};
	 	declareInlets(ins); 

		declareOutlets(new int[]{ DataTypes.INT, DataTypes.FLOAT,DataTypes.ALL});

		setInletAssist(INLET_ASSIST);
		setOutletAssist(OUTLET_ASSIST);

		 _maxdelay = 0.1f;
		_xspeaker = new float[_nspk];
 		_yspeaker = new float[_nspk];
		_xListening = new float[_npoints];
 		_yListening = new float[_npoints];
		_R =new double[_npoints*_nspk];
		 _dint =new int[_npoints*_nspk];
		_coefs =new double[_npoints*_nspk*5];
		 _c = 340;
		_fech=44100;
		_xdelta =0.03f;
		_ydelta =0.03f;

	}

//ENTREES

	public void bang()
	{
	int Rt;
	int i,t,l,nout,ncoef,nd,nv,Lbuff,j,k,seq;
	double d;
	double d_lagrange,v;
	float[] vsignal;
	vsignal=new float[_npoints];
	seq=0;
	RCompute();
	CoefsCompute();
	t=0;
	seq=0;
	v=2.3;

			for (k=0; k< _nspk; k++)
				{
				seq=(int)k*_buffersize1;

				for (nout=0; nout<_npoints;nout++)
					{
					nd=nout*_nspk +k;
					t = _buffersize1 - _dint[nd];
					v = 0.0;
					for (l=0; l<5; l++)
						{
						ncoef=nout*_nspk*5  + k*5 + l;
						v = v + _coefs[ncoef] * _buffer[t+seq];
						t = t-1;
						if (t<0) { t = t + _buffersize1; }
						}
					vsignal[nout]=vsignal[nout]+  (float)(v/(1+_R[nd]));
					}
				}
				outlet(0,vsignal[0]);
				outlet(1,(vsignal[1]-vsignal[0])/_xdelta);
				outlet(2,(vsignal[2]-vsignal[0])/_ydelta);
				for (j=0;j<3;j++){ vsignal[j]=0.f;}

			}


	public void anything(String msg, Atom[] args)
	{
	if (new String("Source").equals(msg))
		{int i;

            i= args[0].getInt()-1;
			if (i< _nspk){
            _xspeaker[i]=args[1].getFloat();
            _yspeaker[i]=args[2].getFloat();
//			post("new source");
   			 }        
	}
	if (new String("Listener").equals(msg))
		{int i;
            i= args[0].getInt()-1;
			if (i<1) {
            _xListening[0]=args[1].getFloat();
            _yListening[0]=args[2].getFloat();
			_xListening[1]= _xListening[0]+_xdelta;
			_yListening[1]= _yListening[0];
			_xListening[2]= _xListening[0];
			_yListening[2]= _yListening[0]+_ydelta;
//			post("new Listener");
			}            
            
	}

	}


//DSP Prog

	public void dspsetup(MSPSignal[] ins, MSPSignal[] outs)
	{ int i;
		_buffersize1=(int)(Math.ceil(ins[0].sr * _maxdelay) + 3); // 3 extra samples for lagrange interp
		_buffer = new float[_buffersize1*_nspk];
		_cur = new int[_nspk];
		for (i=0; i<_nspk; i++)
		{_cur[i]=0;
		}
//		_cur=0;
		_fech=ins[0].sr;

	}

	public void perform(MSPSignal[] ins, MSPSignal[] outs)
	{
		int i,k,seq,Lbuff;
		Lbuff=ins[0].vec.length;



	for (k=0; k< _nspk; k++)
			{
			seq=(int)k*_buffersize1;
			for (i = 0; i<Lbuff;i++)
			{
				_buffer[_cur[k]+seq] = ins[k].vec[i];
				_cur[k]++;
				if (_cur[k] >= _buffersize1) _cur[k]=0;
				}

			}
}


//Prog

	private void RCompute()
	{
		int i,k,curR;
		curR=0;

		for (k =0 ; k<_npoints ; k++)
		{
			for (i = 0 ; i<_nspk ; i++)
			{
					_R[curR]=Math.sqrt( Math.pow(_xspeaker[i]-_xListening[k],2) + Math.pow(_yspeaker[i]-_yListening[k],2) );
					curR++;
			}
		}

	}

	private double[] lagrange5(double d)
	{
		double[] coefs = { (d-1)*(d-2)*(d-3)*(d-4)/24.0,
				 -d*(d-2)*(d-3)*(d-4)/6.0,
				 d*(d-1)*(d-3)*(d-4)/4.0,
				 -d*(d-1)*(d-2)*(d-4)/6.0,
				 d*(d-1)*(d-2)*(d-3)/24.0 };

		return coefs;
				 
	}

	private void CoefsCompute()
	{
	int nout,k,i,curC,curR,dint;
	double d,d_lagrange;
	double[] coefs;
	curC=0;
	curR=0;
	coefs= new double[5];

	for (nout=0; nout<_npoints;nout++)
		{
		for (k=0; k< _nspk; k++)
			{
			d= _R[curR]/(double)_c*_fech;
			if (d<2.0){ d = 2.0; }
			if (d>(_buffersize1-3)){d = _buffersize1-3;}

			dint = (int)Math.round(d)-2;
		
			_dint[curR]=dint;
			d_lagrange = d - dint;
			coefs = lagrange5(d_lagrange);
			
			for (i=0; i<5; i++)
				{
				_coefs[curC]=coefs[i];
				curC++;
				}
			curR++;	
			}
		}	
	}

}





