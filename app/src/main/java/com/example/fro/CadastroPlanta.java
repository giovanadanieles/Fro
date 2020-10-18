package com.example.fro;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

/**
 * Referencia (tirar foto e utilizar): https://developer.android.com/training/camera/photobasics?hl=pt-br
 * Referencia (converter de bitmap para Base64): https://stackoverflow.com/questions/9224056/android-bitmap-to-base64-string
 * **/
public class CadastroPlanta extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.activity_cadastro_planta);
        int tipo = (int) intent.getSerializableExtra("tipo");
        if(tipo == 1) { /*!< Reconhecimento */
            reconhecimentoDePlanta();
        } else { /*!< Manual */
            reconhecimentoManual();
        }
    }

    static final int REQUEST_IMAGE_CAPTURE = 1;
    private void reconhecimentoDePlanta() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void reconhecimentoManual() {
        try {
            Spinner tipoPlantaSpinner = (Spinner) findViewById(R.id.tipoPlantaSpinner);
            BancoDePlantas bancoDePlantas = new BancoDePlantas();

            tipoPlantaSpinner.setVisibility(View.VISIBLE);

            /*!< Criando Spinner */
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, bancoDePlantas.getNomesPlantas());
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            tipoPlantaSpinner.setAdapter(adapter);

            /*!< Quando o usuário atualiza a lista de tipos de plantas, irá atualizar a imagem apresentada. A imagem sera uma padrao para cada planta localizada em /drawable */
            tipoPlantaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                    String uri = "@drawable/" + bancoDePlantas.getUrlPlantas().get(position);
                    int imageResource = getResources().getIdentifier(uri, null, getPackageName()); /*!< Pegando o resource da imagem */
                    Drawable res = getResources().getDrawable(imageResource);
                    ImageView fotoDaPlanta = findViewById(R.id.fotoDaPlanta);
                    /*!< Atualizando imagem no ImageView */
                    fotoDaPlanta.setImageDrawable(res);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }

            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");

            /*!< Conversao de bitmap para byte array */
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream .toByteArray();

            /*!< Convertendo a imagem para Base64 */
            String imagemPlantaBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT);

            try {
                /*!< Chamando a API */
                String respostaPlantId = new PlantId(imagemPlantaBase64).execute().get();

                /*!< Objeto JSON da resposta da API */
                JSONObject plantaJson = new JSONObject(respostaPlantId);

                /*!< Pegando lista de sugestoes de plantas */
                JSONArray sugestoes = plantaJson.getJSONArray("suggestions");

                /*!< Pegando a primeira sugestao */
                JSONObject primeiraSugestao = sugestoes.getJSONObject(0);

                /*!< Componentes da tela que serao apresentados dependendo do resultado da API */
                ImageView fotoDaPlanta = findViewById(R.id.fotoDaPlanta);
                TextView tipoPlantaTexto = findViewById(R.id.tipoPlantaTexto);
                tipoPlantaTexto.setVisibility(View.INVISIBLE);
                Spinner tipoPlantaSpinner = (Spinner) findViewById(R.id.tipoPlantaSpinner);
                tipoPlantaSpinner.setVisibility(View.INVISIBLE);

                /*!< Probabilidade da API acertar deve ser maior que 30% */
                double probabilidade = primeiraSugestao.getDouble("probability");
                if(probabilidade >= 0.3) { /*!< Utiliza a informação da API */
                    /*!< Pegando os detalhes das plantas */
                    JSONObject detalhesDaPrimeiraSugestao = new JSONObject(primeiraSugestao.getString("plant_details"));

                    /*!< Pegando array com os nomes comuns da planta */
                    JSONArray nomesComunsDaPrimeiraSugestao = detalhesDaPrimeiraSugestao.getJSONArray("common_names");

                    /*!< Identificando a planta */
                    BancoDePlantas bancoDePlantas = new BancoDePlantas();
                    Planta planta = bancoDePlantas.identificarPlanta(nomesComunsDaPrimeiraSugestao);

                    /*!< Configurando coisas da tela */
                    fotoDaPlanta.setImageBitmap(imageBitmap);
                    tipoPlantaTexto.setVisibility(View.VISIBLE);
                    tipoPlantaTexto.setText("Nome: " + planta.getNome());
                } else { /*!< Como não achou na API, habilita o Spinner para seleção manual */
                    reconhecimentoManual();
                }
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } else { /*!< Foto não capturada com sucesso*/
            /*!< Abrir tela de selecao de tipo de cadastro de planta (reconhecimento ou manual) */
            Intent intent = new Intent(this, TipoCadastroPlanta.class);
            startActivity(intent);
        }

    }


    /*!< Funcao responsavel por baixar imagem do usuario */
    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }

}