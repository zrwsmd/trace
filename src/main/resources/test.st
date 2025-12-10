PROGRAM disControl
VAR
	status_Z:BOOL;
	DI_Zero:BOOL;//参考点
	DI_Limit:BOOL;
	DO_disRdy:BOOL;
	DI_EMG:BOOL;
	step:INT;
	ton1:Standard.TON;
	disAxisMag_CSV:SNK.AxisManage_CSV;
	power:SM3_Basic.MC_Power;
	ServerOn:BOOL;
	MoveVelocity:SM3_Basic.MC_MoveVelocity;
	Halt:SM3_Basic.MC_Halt;
	CamTableSelect2:SM3_Basic.MC_CamTableSelect;
	CamIn2:SM3_Basic.MC_CamIn;
	SetPosition:SM3_Basic.MC_SetPosition;
	t:LREAL:=0;
	count_z:INT;
END_VAR


//出料控制程序
power(
	Axis:=disAxis , 
	Enable:=TRUE , 
	bRegulatorOn:=ServerOn AND NOT DI_Limit  , 
	bDriveStart:=ServerOn  AND NOT DI_Limit );
//前极限开关
IF DI_Limit THEN
	step:=0;
END_IF

CASE step OF 
	0:
		DO_disRdy:=FALSE;
		IF %IW8>0 THEN//以前%IW32，没有找到为什么是32
			t:=t+0.002;
		END_IF
		
		IF t>20  THEN
		//IF t>8 AND disAxisMag_CSV.sw_Ready THEN			
			ServerOn:=TRUE;
		END_IF
		IF power.Status AND NOT DI_EMG  THEN	//开始回零	
		//IF power.Status AND disAxisMag_CSV.sw_SwitchOn THEN	//开始回零		
			MoveVelocity.Execute:=TRUE;
			MoveVelocity.Direction:=negative;
			MoveVelocity.Velocity:=10;
			step:=1;
		END_IF
	1:
		IF DI_Zero THEN//停止
			MoveVelocity.Execute:=FALSE;
			Halt.Execute:=TRUE;
			step:=2;
		END_IF
	2:
		IF Halt.Done THEN//向前慢速移动
			Halt.Execute:=FALSE;
			MoveVelocity.Execute:=TRUE;
			MoveVelocity.Direction:=positive;
			MoveVelocity.Velocity:=0.1;			
			step:=3;
		END_IF
	3:
		IF NOT DI_Zero THEN//脱开接近开关,快速停止
			Halt.Execute:=TRUE;
			MoveVelocity.Execute:=FALSE;
			SetPosition.Execute:=TRUE;
			t:=0;
			step:=4;	
		END_IF
	4:
		t:=t+0.002;
		IF Halt.Done AND t>0.1 THEN//再次停止后		
			Halt.Execute:=FALSE;				
			step:=5;
		END_IF
	5:
		DO_disRdy:=TRUE;
		IF tubeEncoder.fActPosition>350  THEN
			t:=0;
			step:=6;
		END_IF	
	6:		
		t:=t+0.002;
		CamTableSelect2.Execute:=TRUE;
		IF CamTableSelect2.Done AND t>0.2 
			AND tubeEncoder.fActPosition>0 
			AND tubeEncoder.fActPosition<10 THEN
			camin2.Execute:=TRUE;
			camin2.MasterOffset:=0 ;
			camin2.SlaveOffset:= 0 ; 
			camin2.MasterScaling:=1 ; 
			camin2.SlaveScaling:= 1; 
			camin2.StartMode:= absolute; 
			camin2.Acceleration:=10;
			camin2.Deceleration:=10;
			camin2.Jerk:=100;
			camin2.CamTableID:=CamTableSelect2.CamTableID ;	
			calcam();
			step:=7;
		END_IF
	7:
		CamTableSelect2.Execute:=FALSE;
		camin2.Execute:=FALSE;

		IF CamIn2.EndOfProfile THEN
			calcam();
		END_IF
END_CASE

	
	
MoveVelocity(
	Axis:=disAxis , 
	Execute:= , 
	Velocity:= , 
	Acceleration:=10 , 
	Deceleration:=10 , 
	Jerk:=100 , 
	Direction:= , 
	InVelocity=> , 
	Busy=> , 
	CommandAborted=> , 
	Error=> , 
	ErrorID=> );

Halt(
	Axis:=disaxis , 
	Execute:= , 
	Deceleration:=100 , 
	Jerk:=500 , 
	Done=> , 
	Busy=> , 
	CommandAborted=> , 
	Error=> , 
	ErrorID=> );	

	
CamTableSelect2(
	Master:=tubeEncoder  , 
	Slave:=disaxis , 
	CamTable:=cam2 , 
	Execute:= , 
	Periodic:=TRUE , 
	MasterAbsolute:= TRUE, 
	SlaveAbsolute:= TRUE );

	
CamIn2(
	Master:=tubeEncoder  , 
	Slave:=disAxis , 
	Execute:= , 
	MasterOffset:= , 
	SlaveOffset:= , 
	MasterScaling:= , 
	SlaveScaling:= , 
	StartMode:= , 
	CamTableID:= , 
	VelocityDiff:= , 
	Acceleration:= , 
	Deceleration:= , 
	Jerk:= , 
	TappetHysteresis:= , 
	InSync=> , 
	Busy=> , 
	CommandAborted=> , 
	Error=> , 
	ErrorID=> , 
	EndOfProfile=> , 
	Tappets=> );	

SetPosition(
	Axis:=disaxis , 
	Execute:= , 
	Position:=0 , 
	Mode:=FALSE , 
	Done=> , 
	Busy=> , 
	Error=> , 
	ErrorID=> );
SetPosition.Execute:=FALSE;	


HMI[10]:=INT_TO_WORD(LREAL_TO_INT(tubeEncoder.fActPosition));

IF status_Z THEN
	count_z:=count_z+1;
END_IF