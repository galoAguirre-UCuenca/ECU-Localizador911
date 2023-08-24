#include <Wire.h>

#include <LiquidCrystal_I2C.h>


// Set the LCD address to 0x27 for a 16 chars and 2 line display

LiquidCrystal_I2C lcd(0x27, 20, 4);


uint8_t number = 0x01;

uint8_t number_2 = 0x01;

bool signal;

char url[] = "https://www.google.com/maps/search/?api=1&query=";

char secuencia[40];

int counter = 0;

int index = 0;

int conteo = 0;


void setup() {


  Serial.begin(9600);


  pinMode(3, INPUT);  //  PIN PARA DETECTAR QUE LLEGO UN DIGITO

  pinMode(4, INPUT);

  pinMode(5, INPUT);

  pinMode(6, INPUT);

  pinMode(7, INPUT);


  	// initialize the LCD
	lcd.begin();

	// Turn on the blacklight and print a message.
	lcd.backlight();

	//lcd.print("Bienvenidos.");

  lcd.clear();

  lcd.setCursor(0,0);

  lcd.print("Bienvenido al");

  lcd.setCursor(0,1);

  lcd.print("Sistema de");

  lcd.setCursor(0,2);

  lcd.print("Geolocalizacion de");

  lcd.setCursor(0,3);

  lcd.print("Llamadas Moviles.");

  conteo = 0;

  index = 0;

  delay(4000);

  lcd.clear();

}


void loop() {



  signal = digitalRead(3);



  if (signal == HIGH)   /* If new pin pressed */  // GRAK: LLEGO UN DIGITO
  {


    delay(250);   /*  GRAC: PORQUE? */


    //  GRAC: CONVIERTE LOS PINS A UN SIMBOLO HEXADECIMAL

    number = (0x00 | (digitalRead(7) << 0) | (digitalRead(6) << 1) | (digitalRead(5) << 2) | (digitalRead(4) << 3));



    switch (number) {


      case 0x01:
      

        Serial.print("1");

        secuencia [ counter ] = '1';

        counter ++;

        lcd.print("1");

        conteo += 1;

        //conteo += 2;    //  FORZANNDO UN ERROR EN LA PARIDAD

        break;


      case 0x02:


        Serial.print("2");

        secuencia [ counter ] = '2';

        counter ++;

        lcd.print("2");

        conteo += 2;

        break;


      case 0x03:


        Serial.print("3");

        secuencia [ counter ] = '3';

        counter ++;

        lcd.print("3");

        conteo += 3;

        break;


      case 0x04:


        Serial.print("4");

        secuencia [ counter ] = '4';

        counter ++;

        lcd.print("4");

        conteo += 4;

        break;


      case 0x05:


        Serial.print("5");

        secuencia [ counter ] = '5';

        counter ++;

        lcd.print("5");

        conteo += 5;

        break;


      case 0x06:


        Serial.print("6");

        secuencia [ counter ] = '6';

        counter ++;

        lcd.print("6");

        conteo += 6;

        break;


      case 0x07:


        Serial.print("7");

        secuencia [ counter ] = '7';

        counter ++;

        lcd.print("7");

        conteo += 7;

        break;


      case 0x08:


        Serial.print("8");

        secuencia [ counter ] = '8';

        counter ++;

        lcd.print("8");

        conteo += 8;

        break;


      case 0x09:


        Serial.print("9");

        secuencia [ counter ] = '9';

        counter ++;

        lcd.print("9");

        conteo += 9;

        break;


      case 0x0A:


        Serial.print("0");

        secuencia [ counter ] = '0';

        counter ++;

        lcd.print("0");

        conteo += 10;

        break;


      case 0x0B:

        //    GRAC: TODO: WOULD HAVE TO ADD CODE HERE


        //    PUNTO (.)

        conteo += 11;

        ////Serial.print("*");

        if (number_2 == 0x0B) {


          Serial.print(".");

          secuencia [ counter ] = '.';

          counter ++;

          lcd.print(".");

          ///conteo += 13;



        //    FIN DE LA SECUENCIA

        } else if (number_2 == 0x0C) {
          Serial.println(""); ///

          //    GRAC: FIN DE LA SECUENCIA, IMPRIME TODO          

          //lcd.setCursor(0,0);
          

          //Serial.println(url+secuencia[0,counter-5]);

          Serial.println(""); ///

          //Serial.println(conteo);

          ////Serial.println("CONTEO : ");

          ////Serial.println(conteo-34);

          ////Serial.println("SECUENCIA : ");

          ////Serial.println( secuencia );

          ////Serial.println("COUNTER : ");

          ////Serial.println( counter );


          ////Serial.println("Ultimos Secuencia:");

          ////Serial.println(secuencia[counter]);

          ////Serial.println(secuencia[counter-1]);

          ////Serial.println(secuencia[counter-2]);

          ////Serial.println(secuencia[counter-3]);

          ////Serial.println(secuencia[counter-4]);


          if( secuencia[counter-4]=='0' )
          {
            ////Serial.println("P");            
            

            ////Serial.println("CONTEO : ");
            
            ////Serial.println(conteo-34);
            
            
            ////Serial.println(" Secuencia:");
            
            ////Serial.println(secuencia[counter-4]);    


            if( ( conteo-44)%2!=0  ){

              Serial.println("ERROR DE PARIDAD!");

              ////Serial.println(conteo-44);

              lcd.print("ERROR DE PARIDAD!");

            }else{

              ////Serial.println("Todo bien!");

              ////Serial.println(conteo-44);

            }


          }else if( secuencia[counter-4]=='1' )
          {

            ////Serial.println("I");


            ////Serial.println("CONTEO : ");          

            ////Serial.println(conteo-34);
            
            
            ////Serial.println(" Secuencia:");

            ////Serial.println(secuencia[counter-4]); 


            if( ( conteo-35)%2==0  ){

              Serial.println("Error, not Even!");

              ////Serial.println(conteo-35);

              lcd.print("ERROR DE PARIDAD!");

            }else{

              ////Serial.println("Todo bien!");

              ////Serial.println(conteo-35);
            }

          }else{

            ////Serial.println("?");

          }
          index=0;
          lcd.setCursor(0,index);

          lcd.print("                    ");

          lcd.setCursor(0,index);


          Serial.println("");

          Serial.println("");

          Serial.print( url );

          
          for (byte i = 0; i < counter-12; i = i + 1) {
            Serial.print(secuencia[i]);
          }
          

          //lcd.print( secuencia );

          Serial.println("");   //  I DON'T THINK IT'S NECESSARY

          Serial.println("***");   //    NO SE SI ESTO ES NECESARIO? ///

          number = 0x01;

          counter = 0;


          

          conteo=0;

          
        }

        break;

      case 0x0C:

        //  GRAC: WOULD HAVE TO ADD CODE HERE

        conteo += 12;

        //Serial.print("#");  ///

        //  COMMA (,)
        if (number_2 == 0x0B) {

          Serial.print(",");  ///

          //Serial.print( "%2C" );

          secuencia [ counter ] = '%';

          counter ++;

          secuencia [ counter ] = '2';

          counter ++;

          secuencia [ counter ] = 'C';

          counter ++;

          //lcd.print(",");

          index = index + 1;

          if (index >=4) 
          {

            index=0;
          
          }

          lcd.setCursor(0,index);

          lcd.print("                    ");

          lcd.setCursor(0,index);

          ///conteo += 11;

        //  SIGNO NEGATIVO (-)  
        } else if (number_2 == 0x0C) {

          Serial.print("-");  ///

          secuencia [ counter ] = '-';

          counter ++;

          lcd.print("-");

          ///conteo += 12;
        }

        break;
    }
  }

  number_2 = number;
}
